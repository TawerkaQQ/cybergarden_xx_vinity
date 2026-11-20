from pathlib import Path

from pipeline.domain.ports import ArticleReader, PagesStore


class BuildPages:
    def __init__(self, reader: ArticleReader, store: PagesStore):
        self._reader = reader
        self._store = store

    def __call__(self, pdf: Path) -> int:
        article = self._reader.read(pdf)
        self._store.save(article)
        return len(article.pages)
