import csv
from pathlib import Path

from pipeline.domain.profile import ISSUE_COLUMNS, Profile

ENCODING = "utf-8-sig"
ISSUES_FILE = "issues.csv"


class CsvTables:
    def __init__(self, profile: Profile):
        self._profile = profile

    def write(self, directory: Path, tables: dict[str, list[dict[str, str]]]) -> None:
        directory.mkdir(parents=True, exist_ok=True)
        for table in self._profile.tables:
            write_rows(directory / table.file, table.columns, tables[table.name])
        issues = directory / ISSUES_FILE
        if not issues.exists():
            write_rows(issues, ISSUE_COLUMNS, [])

    def read_values(self, directory: Path) -> list[dict[str, str]]:
        path = directory / self._profile.values_table.file
        with path.open(encoding=ENCODING, newline="") as source:
            return list(csv.DictReader(source))


def write_rows(path: Path, columns: tuple[str, ...], rows: list[dict[str, str]]) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding=ENCODING, newline="") as target:
        writer = csv.DictWriter(target, fieldnames=list(columns), lineterminator="\r\n", extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)
    temporary.replace(path)
