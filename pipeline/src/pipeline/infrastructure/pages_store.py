from pathlib import Path

from pipeline.domain.article import Article
from pipeline.infrastructure.json_files import write_json


class JsonPagesStore:
    def __init__(self, directory: Path):
        self._directory = directory

    def save(self, article: Article) -> None:
        payload = {
            "publication_id": article.publication_id,
            "parser": article.parser,
            "pages": [{"page": page.number, "text": page.text} for page in article.pages],
        }
        write_json(self._directory / f"{article.publication_id}.json", payload)
