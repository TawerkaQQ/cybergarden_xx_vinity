from pathlib import Path
from typing import Protocol

from pipeline.domain.article import Article


class ArticleReader(Protocol):
    def read(self, pdf: Path) -> Article: ...


class PagesStore(Protocol):
    def save(self, article: Article) -> None: ...
