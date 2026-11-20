from typing import Any

from pipeline.domain.article import Article
from pipeline.domain.extraction_shape import ExtractionShape
from pipeline.domain.ports import LanguageModel
from pipeline.domain.prompts import PromptBook


class ProfileExtraction:
    def __init__(self, model: LanguageModel, shape: ExtractionShape, prompts: PromptBook):
        self._model = model
        self._prompts = prompts
        self._structure_schema = shape.structure_schema()
        self._mentions_schema = shape.mentions_schema()

    def structure(self, article: Article) -> dict[str, Any]:
        request = self._prompts.structure_request(article)
        return self._model.complete(self._prompts.system, request, self._structure_schema)

    def mentions(self, article: Article, structure: dict[str, Any]) -> dict[str, Any]:
        request = self._prompts.mentions_request(article, structure)
        return self._model.complete(self._prompts.system, request, self._mentions_schema)
