import hashlib
import json
from collections.abc import Callable
from pathlib import Path
from typing import Any

from pipeline.domain.article import Article
from pipeline.domain.ports import ExtractionModel
from pipeline.infrastructure.json_files import read_json, write_json


class CachedExtractionModel:
    def __init__(self, model: ExtractionModel, directory: Path, key: str):
        self._model = model
        self._directory = directory
        self._key = key

    def structure(self, article: Article) -> dict[str, Any]:
        return self._cached(article, "structure", lambda: self._model.structure(article))

    def tables(self, article: Article) -> dict[str, Any]:
        return self._cached(article, "tables", lambda: self._model.tables(article))

    def table_mentions(self, article: Article, structure: dict[str, Any], table: dict[str, Any]) -> dict[str, Any]:
        step = f"table-{digest(table)}"
        return self._cached(article, step, lambda: self._model.table_mentions(article, structure, table))

    def mentions(self, article: Article, structure: dict[str, Any]) -> dict[str, Any]:
        return self._cached(article, "mentions", lambda: self._model.mentions(article, structure))

    def _cached(self, article: Article, step: str, compute: Callable[[], dict[str, Any]]) -> dict[str, Any]:
        path = self._directory / hashlib.sha256(article.content).hexdigest() / self._key / f"{step}.json"
        if path.exists():
            return read_json(path)
        result = compute()
        write_json(path, result)
        return result


def digest(payload: Any) -> str:
    return hashlib.sha256(json.dumps(payload, ensure_ascii=False, sort_keys=True).encode()).hexdigest()[:12]
