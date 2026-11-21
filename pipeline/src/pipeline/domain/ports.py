from pathlib import Path
from typing import Any, Protocol

from pipeline.domain.article import Article
from pipeline.domain.extract import ExtractResult


class ArticleReader(Protocol):
    def read(self, pdf: Path) -> Article: ...


class PagesStore(Protocol):
    def save(self, article: Article) -> None: ...


class ExtractStore(Protocol):
    def save(self, result: ExtractResult) -> None: ...


class LanguageModel(Protocol):
    def complete(self, system: str, user: str, schema: dict[str, Any]) -> dict[str, Any]: ...


class ExtractionModel(Protocol):
    def structure(self, article: Article) -> dict[str, Any]: ...

    def tables(self, article: Article) -> dict[str, Any]: ...

    def table_mentions(self, article: Article, structure: dict[str, Any], table: dict[str, Any]) -> dict[str, Any]: ...

    def mentions(self, article: Article, structure: dict[str, Any]) -> dict[str, Any]: ...
