from pathlib import Path
from typing import Any, Protocol

from pipeline.domain.article import Article
from pipeline.domain.extract import ExtractResult
from pipeline.domain.highlight import Highlight


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

    def cited_mentions(self, article: Article, structure: dict[str, Any]) -> dict[str, Any]: ...


class WorkSource(Protocol):
    def extracts(self, publication_ids: list[str]) -> dict[str, dict[str, Any]]: ...

    def pages(self, publication_ids: list[str]) -> dict[str, dict[int, str]]: ...


class TableStore(Protocol):
    def write(self, directory: Path, tables: dict[str, list[dict[str, str]]]) -> None: ...

    def read_values(self, directory: Path) -> list[dict[str, str]]: ...


class PageGeometry(Protocol):
    def page_sizes(self, publication_id: str) -> dict[int, tuple[float, float]]: ...

    def locate(self, publication_id: str, page: int, fragment: str, value: str) -> Highlight | None: ...


class ReportStore(Protocol):
    def write(self, directory: Path, report: dict[str, Any]) -> None: ...

    def read(self, directory: Path, publication_id: str) -> dict[str, Any]: ...
