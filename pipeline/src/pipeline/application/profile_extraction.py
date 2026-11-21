from typing import Any

from pipeline.domain.article import Article
from pipeline.domain.extraction_shape import ExtractionShape
from pipeline.domain.ports import LanguageModel
from pipeline.domain.prompts import PromptBook

EMPTY_RESULT_RETRIES = 2


class ProfileExtraction:
    def __init__(self, model: LanguageModel, shape: ExtractionShape, prompts: PromptBook):
        self._model = model
        self._prompts = prompts
        self._structure_schema = shape.structure_schema()
        self._tables_schema = shape.tables_schema()
        self._mentions_schema = shape.mentions_schema()

    def structure(self, article: Article) -> dict[str, Any]:
        return self._ask(self._prompts.structure_request(article), self._structure_schema)

    def tables(self, article: Article) -> dict[str, Any]:
        return self._ask(self._prompts.tables_request(article), self._tables_schema)

    def table_mentions(self, article: Article, structure: dict[str, Any], table: dict[str, Any]) -> dict[str, Any]:
        return self._ask(self._prompts.table_request(article, structure, table), self._mentions_schema)

    def mentions(self, article: Article, structure: dict[str, Any]) -> dict[str, Any]:
        request = self._prompts.mentions_request(article, structure)
        result = self._ask(request, self._mentions_schema)
        for _ in range(EMPTY_RESULT_RETRIES):
            if result["mentions"]:
                break
            result = self._ask(request, self._mentions_schema)
        return result

    def _ask(self, request: str, schema: dict[str, Any]) -> dict[str, Any]:
        return self._model.complete(self._prompts.system, request, schema)
