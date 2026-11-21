from pathlib import Path

from pipeline.domain.ports import TableStore
from pipeline.domain.scoring import Score, Scorer


class ScoreSet:
    def __init__(self, scorer: Scorer, tables: TableStore):
        self._scorer = scorer
        self._tables = tables

    def __call__(self, ours: Path, gold: Path, mode: str, primary_only: bool) -> dict[str, Score]:
        return self._scorer.score(self._tables.read_values(ours), self._tables.read_values(gold), mode, primary_only)
