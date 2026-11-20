import hashlib
from collections.abc import Callable
from pathlib import Path

from pydantic import BaseModel

from pipeline.domain.article import Article
from pipeline.domain.extract import ArticleMentions, ArticleStructure
from pipeline.domain.ports import ExtractionModel
from pipeline.infrastructure.json_files import read_json, write_json


class CachedExtractionModel:
    def __init__(self, model: ExtractionModel, directory: Path, prompt_version: str):
        self._model = model
        self._directory = directory
        self._prompt_version = prompt_version

    def structure(self, article: Article) -> ArticleStructure:
        return self._cached(article, "structure", ArticleStructure, lambda: self._model.structure(article))

    def mentions(self, article: Article, structure: ArticleStructure) -> ArticleMentions:
        return self._cached(article, "mentions", ArticleMentions, lambda: self._model.mentions(article, structure))

    def _cached[T: BaseModel](self, article: Article, step: str, model: type[T], compute: Callable[[], T]) -> T:
        path = self._path(article, step)
        if path.exists():
            return model.model_validate(read_json(path))
        result = compute()
        write_json(path, result.model_dump(mode="json"))
        return result

    def _path(self, article: Article, step: str) -> Path:
        digest = hashlib.sha256(article.content).hexdigest()
        return self._directory / f"{digest}-{self._prompt_version}" / f"{step}.json"
