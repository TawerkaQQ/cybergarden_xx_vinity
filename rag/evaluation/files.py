import csv
import hashlib
import json
from collections import defaultdict
from contextlib import contextmanager
from decimal import Decimal, InvalidOperation
from pathlib import Path
from tempfile import NamedTemporaryFile
from typing import Iterable

from evaluation.extraction import FIGURE, TABLE, Candidate, Observation

SCORED_COLUMNS = {
    "publication_id", "metric_name", "metric_k", "result_origin", "source_page",
    "source_table", "source_figure", "extraction_status",
}


def read_results(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as file:
        reader = csv.DictReader(file)
        fields = set(reader.fieldnames or ())
        if not SCORED_COLUMNS <= fields or not {"normalized_value", "metric_value"} & fields:
            raise ValueError(f"{path}: не хватает колонок для сравнения результатов")
        rows = []
        for row in reader:
            if None in row or any(value is None for value in row.values()):
                raise ValueError(f"{path}: неверное число ячеек в строке {reader.line_num}")
            rows.append(row)
        return rows


def read_observations(path: Path) -> dict[str, list[Observation]]:
    observations = defaultdict(list)
    for row in read_results(path):
        try:
            if row["extraction_status"] != "extracted":
                raise ValueError("ожидался числовой результат бенчмарка")
            value = Decimal(row.get("normalized_value") or row["metric_value"])
            page = int(row["source_page"])
            cutoff = int(row["metric_k"]) if row["metric_k"] else None
            if not value.is_finite() or page < 1 or not row["source_fragment"] or not row["raw_value"]:
                raise ValueError("неверное значение или ссылка на источник")
            candidate = Candidate(page, row["raw_value"], value, row["source_fragment"])
            observations[row["publication_id"]].append(Observation(
                candidate, row["metric_name"], row["metric_unit"], cutoff, row["result_origin"],
                row["cited_publication_id"], row["source_table"] or row["source_figure"],
            ))
        except (KeyError, ValueError, InvalidOperation) as error:
            raise ValueError(f"{path}: ожидался results.csv, созданный бенчмарком") from error
    return observations


def write_results(
    path: Path,
    columns: list[str],
    articles: Iterable[tuple[str, list[Observation]]],
) -> None:
    with atomic_text(path, encoding="utf-8-sig") as file:
        writer = csv.DictWriter(file, fieldnames=columns, lineterminator="\r\n")
        writer.writeheader()
        for publication_id, observations in articles:
            for index, observation in enumerate(observations, 1):
                writer.writerow(result_row(publication_id, index, observation))


def result_row(publication_id: str, index: int, observation: Observation) -> dict:
    candidate = observation.candidate
    return {
        "publication_id": publication_id,
        "result_id": f"R{index:05}",
        "metric_name": observation.metric,
        "metric_k": observation.k,
        "metric_value": str(candidate.value),
        "metric_unit": observation.unit,
        "raw_value": candidate.raw,
        "raw_unit": "%" if candidate.raw.endswith("%") else "",
        "normalized_value": str(candidate.value),
        "normalized_unit": observation.unit,
        "result_origin": observation.origin,
        "cited_publication_id": observation.citation if observation.origin == "cited" else "",
        "source_page": candidate.page,
        "source_table": observation.source if TABLE.match(observation.source) else "",
        "source_figure": observation.source if FIGURE.match(observation.source) else "",
        "source_fragment": candidate.quote,
        "extraction_status": "extracted",
    }


@contextmanager
def atomic_text(path: Path, *, encoding: str = "utf-8"):
    path.parent.mkdir(parents=True, exist_ok=True)
    file = NamedTemporaryFile(mode="w", dir=path.parent, encoding=encoding, newline="", delete=False)
    temporary = Path(file.name)
    try:
        with file:
            yield file
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def write_json(path: Path, data: object) -> None:
    with atomic_text(path) as file:
        json.dump(data, file, ensure_ascii=False, indent=2)
        file.write("\n")


def digest(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()
