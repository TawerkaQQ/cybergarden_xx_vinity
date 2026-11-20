from pathlib import Path

from pipeline.domain.extract import ExtractResult
from pipeline.infrastructure.json_files import write_json


class JsonExtractStore:
    def __init__(self, directory: Path):
        self._directory = directory

    def save(self, result: ExtractResult) -> None:
        write_json(self._directory / f"{result.publication_id}.json", result.model_dump(mode="json"))
