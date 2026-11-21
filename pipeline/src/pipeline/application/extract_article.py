import re
from collections import Counter
from pathlib import Path
from typing import Any

from pipeline.domain.article import Article
from pipeline.domain.extract import ExtractResult
from pipeline.domain.extraction_shape import IN_SCOPE, ExtractionShape
from pipeline.domain.ports import ArticleReader, ExtractionModel, ExtractStore

NO_MENTIONS = {"mentions": []}


class ExtractArticle:
    def __init__(
        self,
        reader: ArticleReader,
        model: ExtractionModel,
        shape: ExtractionShape,
        prompt_version: str,
        store: ExtractStore,
    ):
        self._reader = reader
        self._model = model
        self._shape = shape
        self._prompt_version = prompt_version
        self._store = store

    def __call__(self, pdf: Path) -> ExtractResult:
        article = self._reader.read(pdf)
        structure = self._model.structure(article)
        if structure["publication"].get(IN_SCOPE):
            mentions = {"mentions": self._mentions(article, structure)}
        else:
            mentions = NO_MENTIONS
        result = self._shape.result(article.publication_id, self._prompt_version, structure, mentions)
        self._store.save(result)
        return result

    def _mentions(self, article: Article, structure: dict[str, Any]) -> list[dict[str, Any]]:
        mentions = self._model.mentions(article, structure)["mentions"]
        found = Counter(normalized(mention["table"]) for mention in mentions if mention["table"])
        text = normalized(" ".join(page.text for page in article.pages))
        for table in unique_tables(self._model.tables(article)["tables"]):
            name = normalized(table["table"])
            if name not in text or found[name] >= expected_cells(table):
                continue
            from_table = self._model.table_mentions(article, structure, table)["mentions"]
            if len(from_table) > found[name]:
                kept = [mention for mention in mentions if normalized(mention["table"]) != name]
                mentions = kept + [{**mention, "table": table["table"]} for mention in from_table]
        return mentions


def expected_cells(table: dict[str, Any]) -> int:
    return len(table["rows"]) * max(len(table["columns"]) - 1, 1)


def unique_tables(tables: list[dict[str, Any]]) -> list[dict[str, Any]]:
    seen = {}
    for table in tables:
        seen.setdefault(normalized(table["table"]), table)
    return list(seen.values())


def normalized(text: Any) -> str:
    return re.sub(r"\s+", " ", str(text or "")).strip().casefold()
