from collections import Counter
from dataclasses import dataclass

from pipeline.domain.numbers import parse_number
from pipeline.domain.profile import PUBLICATION_ID, Profile

VALUE_ROLES = ("value", "page")


@dataclass(frozen=True)
class Score:
    matched: int
    ours: int
    gold: int

    @property
    def precision(self) -> float:
        return self.matched / self.ours if self.ours else 0.0

    @property
    def recall(self) -> float:
        return self.matched / self.gold if self.gold else 0.0

    @property
    def f1(self) -> float:
        total = self.precision + self.recall
        return 2 * self.precision * self.recall / total if total else 0.0

    def __add__(self, other: "Score") -> "Score":
        return Score(self.matched + other.matched, self.ours + other.ours, self.gold + other.gold)


class Scorer:
    def __init__(self, profile: Profile):
        self._values = profile.values

    def score(
        self, ours: list[dict[str, str]], gold: list[dict[str, str]], mode: str, primary_only: bool = False
    ) -> dict[str, Score]:
        fields = self._values.match[mode]
        ours_keys = self._keys(ours, fields, primary_only)
        gold_keys = self._keys(gold, fields, primary_only)
        return {
            publication_id: Score(
                sum((ours_keys.get(publication_id, Counter()) & gold_keys.get(publication_id, Counter())).values()),
                sum(ours_keys.get(publication_id, Counter()).values()),
                sum(gold_keys.get(publication_id, Counter()).values()),
            )
            for publication_id in sorted(set(ours_keys) | set(gold_keys))
        }

    def _keys(self, rows: list[dict[str, str]], fields: tuple[str, ...], primary_only: bool) -> dict[str, Counter]:
        keys: dict[str, Counter] = {}
        value_column = self._values.column("value")
        metric_column = self._values.column("metric")
        duplicate_column = self._values.column("duplicate_of")
        for row in rows:
            if not row.get(value_column):
                continue
            if self._values.metrics and row.get(metric_column) not in self._values.metrics:
                continue
            if primary_only and row.get(duplicate_column):
                continue
            key = tuple(self._comparable(name, row.get(self._values.column(name), "")) for name in fields)
            keys.setdefault(row[PUBLICATION_ID], Counter())[key] += 1
        return keys

    @staticmethod
    def _comparable(name: str, text: str) -> str:
        if name in VALUE_ROLES:
            number = parse_number(text)
            return f"{number:.6g}" if number is not None else ""
        return " ".join(text.split()).casefold()
