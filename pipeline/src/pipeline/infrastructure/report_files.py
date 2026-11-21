from pathlib import Path
from typing import Any

from pipeline.infrastructure.json_files import read_json, write_json

ARTICLES = "articles"


class JsonReportStore:
    def write(self, directory: Path, report: dict[str, Any]) -> None:
        write_json(directory / ARTICLES / f"{report['publication_id']}.json", report)

    def read(self, directory: Path, publication_id: str) -> dict[str, Any]:
        return read_json(directory / ARTICLES / f"{publication_id}.json")
