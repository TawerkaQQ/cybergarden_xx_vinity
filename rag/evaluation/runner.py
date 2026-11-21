import json
from collections.abc import Callable
from dataclasses import dataclass, field
from functools import partial
from pathlib import Path
from time import sleep
from typing import Protocol

from chat_client import ResponseError, TruncatedResponse
from evaluation import rules, tables
from evaluation.extraction import RESPONSE_SCHEMA, SYSTEM_PROMPT, Observation, Page, annotate, group_pages, resolve
from evaluation.files import digest, write_json
from pdf_text.reader import PdfText

PROMPT_VERSION = "text-v2-" + digest(SYSTEM_PROMPT + json.dumps(RESPONSE_SCHEMA, sort_keys=True))[:12]
TABLE_PROMPT_VERSION = "tables-v3-" + digest(
    tables.SYSTEM_PROMPT + json.dumps(tables.table_schema((1,)), sort_keys=True)
)[:12]
MAX_RESPONSE_TOKENS = 2048


class Completion(Protocol):
    def __call__(self, system: str, user: str, max_tokens: int, *, schema: dict | None = None) -> str: ...


@dataclass
class ArticleRun:
    publication_id: str
    total_pages: int = 0
    text_pages: int = 0
    ocr_pages: int = 0
    graphics_pages: int = 0
    observations: list[Observation] = field(default_factory=list)
    rejected_ids: int = 0
    failed_batches: list[dict] = field(default_factory=list)
    table_blocks: int = 0
    table_cells: int = 0
    table_replaced: int = 0

    def summary(self) -> dict:
        return {
            "total_pages": self.total_pages,
            "text_pages": self.text_pages,
            "ocr_pages": self.ocr_pages,
            "graphics_pages": self.graphics_pages,
            "observations": len(self.observations),
            "rejected_ids": self.rejected_ids,
            "failed_batches": self.failed_batches,
            "table_blocks": self.table_blocks,
            "table_cells": self.table_cells,
            "table_replaced": self.table_replaced,
        }


def extract_article(
    publication_id: str, pdf: PdfText, cache_root: Path, complete: Completion | None, cache_model_id: str,
    previous: list[Observation] | None = None,
) -> ArticleRun:
    run = ArticleRun(publication_id)
    pages = [Page(page.page, page.text, page.graphics_text) for page in pdf.pages]
    run.total_pages = len(pages)
    run.text_pages = sum(bool(page.text.strip() or page.graphics_text.strip()) for page in pages)
    run.ocr_pages = sum(page.engine == "tesseract" for page in pdf.pages)
    run.graphics_pages = sum(bool(page.graphics_text) for page in pdf.pages)
    if complete is None:
        run.observations = [observation for page in pages for observation in rules.observations(page)]
        return run

    model_hash = digest(cache_model_id)[:12]
    article_cache = cache_root / pdf.sha256
    if previous is None:
        cache = article_cache / f"{PROMPT_VERSION}-{model_hash}"
        for batch in group_pages(pages):
            extract_batch(batch, cache, run, complete)
    else:
        run.observations = previous

    extract_tables(pages, article_cache / f"{TABLE_PROMPT_VERSION}-{model_hash}", run, complete)
    return run


def extract_tables(pages: list[Page], cache: Path, run: ArticleRun, complete: Completion) -> None:
    found = []
    for page in pages:
        for block in tables.discover_tables(page, run.observations):
            try:
                panel_columns = request_cached(
                    tables.table_request(block), cache, MAX_RESPONSE_TOKENS, complete,
                    system=tables.SYSTEM_PROMPT, schema=tables.table_schema(block.widths),
                    validate=partial(tables.validate_table, widths=block.widths),
                )
            except (OSError, ValueError) as error:
                run.failed_batches.append({"pages": [block.page], "error": f"{block.caption}: {error}"})
                continue
            observations = [
                observation
                for panel, columns in zip(block.panels, panel_columns, strict=True)
                for observation in tables.table_observations(block, panel, columns)
            ]
            if observations:
                run.table_blocks += 1
                found.extend(observations)
    run.table_cells = len(found)
    run.observations, run.table_replaced = tables.merge_table_observations(run.observations, found)


def extract_batch(
    pages: list[Page], cache: Path, run: ArticleRun, complete: Completion, allowed: set[int] | None = None,
) -> None:
    request, candidates = annotate(pages, allowed)
    if not candidates:
        return
    try:
        groups = request_cached(
            request, cache, MAX_RESPONSE_TOKENS, complete,
            system=SYSTEM_PROMPT, schema=RESPONSE_SCHEMA, validate=validate_groups,
        )
    except TruncatedResponse as error:
        if len(pages) > 1:
            middle = len(pages) // 2
            extract_batch(pages[:middle], cache, run, complete)
            extract_batch(pages[middle:], cache, run, complete)
        elif len(candidates) > 1:
            ids = list(candidates)
            middle = len(ids) // 2
            extract_batch(pages, cache, run, complete, set(ids[:middle]))
            extract_batch(pages, cache, run, complete, set(ids[middle:]))
        else:
            run.failed_batches.append(batch_error(pages, error))
        return
    except (OSError, ValueError) as error:
        run.failed_batches.append(batch_error(pages, error))
        return

    observations, rejected = resolve(groups, candidates)
    run.observations.extend(observations)
    run.rejected_ids += rejected


def batch_error(pages: list[Page], error: Exception) -> dict:
    return {"pages": [page.number for page in pages], "error": str(error)}


def request_cached[T](
    request: str, cache: Path, max_tokens: int, complete: Completion, *,
    system: str, schema: dict, validate: Callable[[object], T],
) -> T:
    file = cache / f"{digest(request)}-{max_tokens}.json"
    try:
        cached = validate(json.loads(file.read_text(encoding="utf-8")))
    except (FileNotFoundError, ValueError):
        pass
    else:
        return cached

    for attempt in range(2):
        try:
            content = complete(system, request, max_tokens, schema=schema)
            break
        except ResponseError as error:
            if attempt or error.status not in {429, 502, 503, 504}:
                raise
        except OSError:
            if attempt:
                raise
        sleep(2)
    payload = json.loads(content)
    rows = validate(payload)
    write_json(file, payload)
    return rows


def validate_groups(payload: object) -> list[dict]:
    if not isinstance(payload, dict) or not isinstance(groups := payload.get("groups"), list):
        raise ValueError("В ответе модели нет списка групп")
    for group in groups:
        if (
            not isinstance(group, dict)
            or any(not isinstance(group.get(key), str) for key in ("metric", "unit", "citation", "source"))
            or not isinstance(group.get("ids"), list)
            or any(type(identifier) is not int for identifier in group["ids"])
        ):
            raise ValueError("Некорректная группа результатов в ответе модели")
    return groups
