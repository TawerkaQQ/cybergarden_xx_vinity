from pathlib import Path
from typing import Any

from pipeline.infrastructure.json_files import read_json


class WorkFiles:
    def __init__(self, directory: Path):
        self._directory = directory

    def extracts(self, publication_ids: list[str]) -> dict[str, dict[str, Any]]:
        paths = {publication_id: self._directory / "extract" / f"{publication_id}.json" for publication_id in publication_ids}
        return {publication_id: read_json(path) for publication_id, path in paths.items() if path.exists()}

    def pages(self, publication_ids: list[str]) -> dict[str, dict[int, str]]:
        pages = {}
        for publication_id in publication_ids:
            path = self._directory / "pages" / f"{publication_id}.json"
            if path.exists():
                pages[publication_id] = {
                    page["page"]: "\n".join(page["texts"].values()) for page in read_json(path)["pages"]
                }
        return pages
