import re
from collections import Counter
from pathlib import Path
from typing import Any

from pipeline.domain.article import Article
from pipeline.domain.consensus import majority
from pipeline.domain.extract import ExtractResult
from pipeline.domain.extraction_shape import IN_SCOPE, ExtractionShape, as_text, with_page_number
from pipeline.domain.numbers import parse_number
from pipeline.domain.ports import ArticleReader, ExtractionModel, ExtractStore

NO_MENTIONS = {"mentions": []}


class ExtractArticle:
    def __init__(
        self,
        reader: ArticleReader,
        models: list[ExtractionModel],
        shape: ExtractionShape,
        prompt_version: str,
        store: ExtractStore,
    ):
        self._reader = reader
        self._models = models
        self._shape = shape
        self._prompt_version = prompt_version
        self._store = store

    def __call__(self, pdf: Path) -> ExtractResult:
        article = self._reader.read(pdf)
        structure = self._models[0].structure(article)
        if structure["publication"].get(IN_SCOPE):
            mentions = {"mentions": majority([self._mentions(model, article, structure) for model in self._models])}
        else:
            mentions = NO_MENTIONS
        result = self._shape.result(article.publication_id, self._prompt_version, structure, mentions)
        self._store.save(result)
        return result

    @staticmethod
    def _mentions(model: ExtractionModel, article: Article, structure: dict[str, Any]) -> list[dict[str, Any]]:
        mentions = [with_page_number(mention) for mention in model.mentions(article, structure)["mentions"]]
        found = Counter(normalized(mention["table"]) for mention in mentions if mention["table"])
        text = normalized(" ".join(page.text for page in article.pages))
        for table in unique_tables([with_page_number(table) for table in model.tables(article)["tables"]]):
            name = normalized(table["table"])
            if name not in text or found[name] >= expected_cells(table):
                continue
            from_table = [with_page_number(mention) for mention in model.table_mentions(article, structure, table)["mentions"]]
            if len(from_table) > found[name]:
                kept = [mention for mention in mentions if normalized(mention["table"]) != name]
                mentions = kept + [{**mention, "table": table["table"]} for mention in from_table]
        known = {mention_key(mention) for mention in mentions}
        cited = [with_page_number(mention) for mention in model.cited_mentions(article, structure)["mentions"]]
        return mentions + [mention for mention in cited if mention_key(mention) not in known]


def mention_key(mention: dict[str, Any]) -> tuple[str, float | None, int]:
    return normalized(mention["metric"]), parse_number(as_text(mention["raw_value"])), mention["page"]


def expected_cells(table: dict[str, Any]) -> int:
    return len(table["rows"]) * max(len(table["columns"]) - 1, 1)


def unique_tables(tables: list[dict[str, Any]]) -> list[dict[str, Any]]:
    seen = {}
    for table in tables:
        seen.setdefault(normalized(table["table"]), table)
    return list(seen.values())


def normalized(text: Any) -> str:
    return re.sub(r"\s+", " ", str(text or "")).strip().casefold()
