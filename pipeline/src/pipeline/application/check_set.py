from collections import Counter
from pathlib import Path

from pipeline.domain.checks import Checker
from pipeline.domain.ports import TableStore


class CheckSet:
    def __init__(self, checker: Checker, tables: TableStore):
        self._checker = checker
        self._tables = tables

    def __call__(self, source: Path, target: Path) -> tuple[list[dict[str, str]], Counter]:
        issues, report = self._checker.check(self._tables.read_tables(source))
        self._tables.write_issues(target, issues)
        return issues, report
