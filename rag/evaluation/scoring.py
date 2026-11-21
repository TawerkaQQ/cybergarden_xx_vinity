import re
import unicodedata
from collections import Counter
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation

CUTOFF = re.compile(r"(.+?)\s*@\s*(\d+)")
SOURCES = ("table", "figure", "text")


@dataclass(frozen=True)
class Value:
    publication_id: str
    page: int
    origin: str
    number: Decimal
    metric: str
    cutoff: int | None
    source: str

    def key(self, strict: bool) -> tuple:
        loose_key = self.publication_id, self.page, self.origin, self.number
        return (*loose_key, self.metric, self.cutoff) if strict else loose_key


def parse_values(rows: list[dict[str, str]]) -> list[Value]:
    values = []
    for row in rows:
        number = (row.get("normalized_value") or "").strip() or (row.get("metric_value") or "").strip()
        if row["extraction_status"] != "extracted" or not number:
            continue
        try:
            decimal_value = Decimal(number)
            page = int(row["source_page"])
            cutoff = int(row["metric_k"]) if row["metric_k"].strip() else None
            if not decimal_value.is_finite() or page < 1 or (cutoff is not None and cutoff < 0):
                raise ValueError
        except (InvalidOperation, ValueError) as error:
            result_id = row.get("result_id", "")
            raise ValueError(f"{row['publication_id']} {result_id}: неверное значение, номер страницы или k") from error

        metric = normalize(row["metric_name"])
        suffix = CUTOFF.fullmatch(metric)
        if suffix and (cutoff is None or cutoff == int(suffix[2])):
            metric, cutoff = suffix[1], int(suffix[2])
        values.append(
            Value(row["publication_id"], page, normalize(row["result_origin"]), decimal_value, metric, cutoff,
                  source_type(row))
        )
    return values


def normalize(text: str) -> str:
    return " ".join(unicodedata.normalize("NFKC", text).casefold().split())


def source_type(row: dict[str, str]) -> str:
    if row["source_table"].strip():
        return "table"
    if row["source_figure"].strip():
        return "figure"
    return "text"


def score(predicted: list[Value], gold: list[Value], strict: bool) -> dict:
    predicted_counts = Counter(value.key(strict) for value in predicted)
    gold_counts = Counter(value.key(strict) for value in gold)
    tp = (predicted_counts & gold_counts).total()
    fp, fn = len(predicted) - tp, len(gold) - tp
    return {
        "predicted": len(predicted),
        "gold": len(gold),
        "tp": tp,
        "fp": fp,
        "fn": fn,
        "precision": tp / len(predicted) if predicted else 0.0,
        "recall": tp / len(gold) if gold else 0.0,
        "f1": 2 * tp / (len(predicted) + len(gold)) if predicted or gold else 0.0,
    }


def both_scores(predicted: list[Value], gold: list[Value]) -> dict:
    return {mode: score(predicted, gold, strict=(mode == "strict")) for mode in ("strict", "loose")}


def build_report(predicted: list[Value], gold: list[Value], publication_ids: set[str]) -> dict:
    by_publication = {}
    for publication_id in sorted(publication_ids):
        by_publication[publication_id] = both_scores(
            [value for value in predicted if value.publication_id == publication_id],
            [value for value in gold if value.publication_id == publication_id],
        )
    macro = {
        mode: {
            measure: sum(row[mode][measure] for row in by_publication.values()) / len(by_publication)
            for measure in ("precision", "recall", "f1")
        }
        for mode in ("strict", "loose")
    }
    by_source = {
        source: both_scores(
            [value for value in predicted if value.source == source],
            [value for value in gold if value.source == source],
        )
        for source in SOURCES
    }
    return {"micro": both_scores(predicted, gold), "macro": macro,
            "by_publication": by_publication, "by_source": by_source}
