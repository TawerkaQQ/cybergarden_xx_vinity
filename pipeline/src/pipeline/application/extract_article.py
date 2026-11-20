from pathlib import Path

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
        in_scope = structure["publication"].get(IN_SCOPE)
        mentions = self._model.mentions(article, structure) if in_scope else NO_MENTIONS
        result = self._shape.result(article.publication_id, self._prompt_version, structure, mentions)
        self._store.save(result)
        return result
