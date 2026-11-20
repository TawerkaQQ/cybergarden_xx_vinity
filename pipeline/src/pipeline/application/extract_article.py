from pathlib import Path

from pipeline.domain.extract import ArticleMentions, ExtractResult
from pipeline.domain.ports import ArticleReader, ExtractionModel, ExtractStore
from pipeline.domain.prompts import PROMPT_VERSION


class ExtractArticle:
    def __init__(self, reader: ArticleReader, model: ExtractionModel, store: ExtractStore):
        self._reader = reader
        self._model = model
        self._store = store

    def __call__(self, pdf: Path) -> ExtractResult:
        article = self._reader.read(pdf)
        structure = self._model.structure(article)
        if structure.publication.in_scope:
            mentions = self._model.mentions(article, structure)
        else:
            mentions = ArticleMentions(mentions=[])
        result = ExtractResult.combine(article.publication_id, PROMPT_VERSION, structure, mentions)
        self._store.save(result)
        return result
