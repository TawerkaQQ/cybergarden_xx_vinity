from pathlib import Path
from typing import Protocol

from pipeline.domain.article import Article
from pipeline.domain.extract import ArticleMentions, ArticleStructure, ExtractResult


class ArticleReader(Protocol):
    def read(self, pdf: Path) -> Article: ...


class PagesStore(Protocol):
    def save(self, article: Article) -> None: ...


class ExtractStore(Protocol):
    def save(self, result: ExtractResult) -> None: ...


class ExtractionModel(Protocol):
    def structure(self, article: Article) -> ArticleStructure: ...

    def mentions(self, article: Article, structure: ArticleStructure) -> ArticleMentions: ...
