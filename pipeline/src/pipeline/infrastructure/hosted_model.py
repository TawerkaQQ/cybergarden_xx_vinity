import hashlib
import json
import threading

import anthropic
from pydantic import BaseModel, ValidationError

from pipeline.domain.article import Article
from pipeline.domain.extract import ArticleMentions, ArticleStructure
from pipeline.domain.prompts import MENTIONS_TASK, STRUCTURE_TASK, SYSTEM_PROMPT
from pipeline.infrastructure.strict_schema import StrictSchema

FALLBACK_BETA = "server-side-fallback-2026-07-01"
UPLOAD_LIFETIME_SECONDS = 6 * 3600
MAX_OUTPUT_TOKENS = 128_000


class ModelResponseError(Exception):
    pass


class HostedExtractionModel:
    def __init__(self, client: anthropic.Anthropic, model: str, effort: str, attempts: int):
        self._client = client
        self._model = model
        self._effort = effort
        self._attempts = attempts
        self._uploads: dict[str, str] = {}
        self._upload_lock = threading.Lock()
        self._structure_schema = StrictSchema(ArticleStructure)
        self._mentions_schema = StrictSchema(ArticleMentions)

    def structure(self, article: Article) -> ArticleStructure:
        return self._ask(article, STRUCTURE_TASK, ArticleStructure, self._structure_schema)

    def mentions(self, article: Article, structure: ArticleStructure) -> ArticleMentions:
        task = f"{MENTIONS_TASK}\nРезультат прохода A:\n{structure.model_dump_json(indent=1)}"
        return self._ask(article, task, ArticleMentions, self._mentions_schema)

    def _ask[T: BaseModel](self, article: Article, task: str, model: type[T], schema: StrictSchema) -> T:
        content = [*self._article_blocks(article), {"type": "text", "text": task}]
        failures = []
        for _ in range(self._attempts):
            try:
                return model.model_validate(schema.decode(self._complete(content, schema)))
            except (ModelResponseError, ValidationError, json.JSONDecodeError, KeyError) as error:
                failures.append(f"{type(error).__name__}: {error}")
        raise ModelResponseError(f"{article.publication_id}: {' | '.join(failures)}")

    def _complete(self, content: list[dict], schema: StrictSchema) -> dict:
        with self._client.beta.messages.stream(
            model=self._model,
            max_tokens=MAX_OUTPUT_TOKENS,
            betas=[FALLBACK_BETA],
            fallbacks="default",
            thinking={"type": "adaptive"},
            output_config={"effort": self._effort, "format": {"type": "json_schema", "schema": schema.json_schema}},
            system=SYSTEM_PROMPT,
            messages=[{"role": "user", "content": content}],
        ) as stream:
            message = stream.get_final_message()
        if message.stop_reason != "end_turn":
            raise ModelResponseError(f"stop_reason={message.stop_reason}")
        text = next(block.text for block in message.content if block.type == "text")
        return json.loads(text)

    def _article_blocks(self, article: Article) -> list[dict]:
        pages = "\n\n".join(f"=== Страница {page.number} ===\n{page.text}" for page in article.pages)
        return [
            {"type": "document", "source": {"type": "file", "file_id": self._upload(article)}},
            {
                "type": "text",
                "text": f"Текст страниц статьи {article.publication_id}, извлечённый pypdf:\n\n{pages}",
                "cache_control": {"type": "ephemeral"},
            },
        ]

    def _upload(self, article: Article) -> str:
        digest = hashlib.sha256(article.content).hexdigest()
        with self._upload_lock:
            if digest not in self._uploads:
                uploaded = self._client.files.upload(
                    file=(f"{article.publication_id}.pdf", article.content, "application/pdf"),
                    expires_in_seconds=UPLOAD_LIFETIME_SECONDS,
                )
                self._uploads[digest] = uploaded.id
            return self._uploads[digest]
