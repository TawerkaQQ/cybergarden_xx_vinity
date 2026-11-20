import argparse
import re
import sys
from pathlib import Path

import openai

from pipeline.application.build_pages import BuildPages
from pipeline.application.extract_article import ExtractArticle
from pipeline.application.folder import article_pdfs
from pipeline.application.process_folder import Outcome, ProcessFolder
from pipeline.application.profile_extraction import ProfileExtraction
from pipeline.domain.extraction_shape import ExtractionShape
from pipeline.domain.prompts import PromptBook
from pipeline.infrastructure.cached_model import CachedExtractionModel
from pipeline.infrastructure.extract_store import JsonExtractStore
from pipeline.infrastructure.openai_model import OpenAiCompatibleModel
from pipeline.infrastructure.pages_store import JsonPagesStore
from pipeline.infrastructure.profile_files import load_profile
from pipeline.infrastructure.pypdf_reader import PypdfArticleReader

DEFAULT_CACHE = Path(__file__).resolve().parents[2] / ".cache"
DEFAULT_MODEL = "qwen3.6-35b-a3b"
REQUEST_TIMEOUT_SECONDS = 1800


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(prog="python -m pipeline")
    commands = parser.add_subparsers(dest="command", required=True)
    pages = commands.add_parser("pages", help="текст страниц PDF в work/<профиль>/pages")
    extract = commands.add_parser("extract", help="извлечение моделью в work/<профиль>/extract")
    for command in (pages, extract):
        command.add_argument("--profile", required=True)
        command.add_argument("--set", dest="sets", nargs="+", required=True)
        command.add_argument("--only", nargs="*")
        command.add_argument("--data", type=Path, default=Path("data"))
        command.add_argument("--work", type=Path, default=Path("work"))
    extract.add_argument("--profiles", type=Path, default=Path("profiles"))
    extract.add_argument("--base-url", help="адрес API vLLM, по умолчанию OPENAI_BASE_URL")
    extract.add_argument("--model", default=DEFAULT_MODEL)
    extract.add_argument("--max-tokens", type=int, default=24576)
    extract.add_argument("--thinking", action="store_true")
    extract.add_argument("--workers", type=int, default=4)
    extract.add_argument("--attempts", type=int, default=3)
    extract.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    return parser.parse_args()


def selected_pdfs(args: argparse.Namespace) -> list[Path]:
    only = set(args.only) if args.only else None
    return [pdf for name in args.sets for pdf in article_pdfs(args.data / args.profile / name, only)]


def run_pages(args: argparse.Namespace) -> int:
    build = BuildPages(PypdfArticleReader(), JsonPagesStore(args.work / args.profile / "pages"))
    for pdf in selected_pdfs(args):
        print(f"{pdf.stem}: {build(pdf)} стр.")
    return 0


def run_extract(args: argparse.Namespace) -> int:
    shape = ExtractionShape(load_profile(args.profiles, args.profile))
    prompts = PromptBook(shape)
    client = openai.OpenAI(base_url=args.base_url, timeout=REQUEST_TIMEOUT_SECONDS, max_retries=5)
    model = OpenAiCompatibleModel(client, args.model, args.max_tokens, args.thinking, args.attempts)
    cache_key = f"{prompts.version}-{slug(args.model)}{'-thinking' if args.thinking else ''}"
    cached = CachedExtractionModel(ProfileExtraction(model, shape, prompts), args.cache, cache_key)
    store = JsonExtractStore(args.work / args.profile / "extract")
    extract = ExtractArticle(PypdfArticleReader(), cached, shape, prompts.version, store)
    print(f"профиль {args.profile}, версия промпта {prompts.version}, модель {args.model}")
    outcomes = ProcessFolder(extract, args.workers)(selected_pdfs(args), report_outcome)
    failed = [outcome.pdf.stem for outcome in outcomes if outcome.error]
    print(f"готово: {len(outcomes) - len(failed)}, ошибок: {len(failed)} {' '.join(failed)}".rstrip())
    return 1 if failed else 0


def report_outcome(outcome: Outcome) -> None:
    if outcome.error:
        print(f"{outcome.pdf.stem}: ошибка {type(outcome.error).__name__}: {outcome.error}", file=sys.stderr)
        return
    result = outcome.result
    records = ", ".join(f"{table} {len(items)}" for table, items in result.records.items())
    print(f"{outcome.pdf.stem}: in_scope={result.in_scope}, {records}, упоминаний {len(result.mentions)}")


def slug(text: str) -> str:
    return re.sub(r"[^a-z0-9.]+", "-", text.lower()).strip("-")


def main() -> int:
    args = parse_args()
    if args.command == "pages":
        return run_pages(args)
    return run_extract(args)


if __name__ == "__main__":
    sys.exit(main())
