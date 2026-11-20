import argparse
import sys
from pathlib import Path

import anthropic

from pipeline.application.build_pages import BuildPages
from pipeline.application.extract_article import ExtractArticle
from pipeline.application.folder import article_pdfs
from pipeline.application.process_folder import Outcome, ProcessFolder
from pipeline.domain.prompts import PROMPT_VERSION
from pipeline.infrastructure.cached_model import CachedExtractionModel
from pipeline.infrastructure.hosted_model import HostedExtractionModel
from pipeline.infrastructure.extract_store import JsonExtractStore
from pipeline.infrastructure.pages_store import JsonPagesStore
from pipeline.infrastructure.pypdf_reader import PypdfArticleReader

DEFAULT_CACHE = Path(__file__).resolve().parents[2] / ".cache"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(prog="python -m pipeline")
    commands = parser.add_subparsers(dest="command", required=True)
    pages = commands.add_parser("pages", help="текст страниц PDF через pypdf")
    extract = commands.add_parser("extract", help="извлечение моделью в work/extract")
    for command in (pages, extract):
        command.add_argument("folders", nargs="+", type=Path)
        command.add_argument("--out", type=Path, default=Path("work"))
        command.add_argument("--only", nargs="*")
    extract.add_argument("--workers", type=int, default=4)
    extract.add_argument("--model", required=True)
    extract.add_argument("--effort", default="high", choices=["low", "medium", "high", "xhigh", "max"])
    extract.add_argument("--attempts", type=int, default=3)
    extract.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    return parser.parse_args()


def selected_pdfs(args: argparse.Namespace) -> list[Path]:
    only = set(args.only) if args.only else None
    return [pdf for folder in args.folders for pdf in article_pdfs(folder, only)]


def run_pages(args: argparse.Namespace) -> int:
    build = BuildPages(PypdfArticleReader(), JsonPagesStore(args.out / "pages"))
    for pdf in selected_pdfs(args):
        print(f"{pdf.stem}: {build(pdf)} стр.")
    return 0


def run_extract(args: argparse.Namespace) -> int:
    reader = PypdfArticleReader()
    model = HostedExtractionModel(anthropic.Anthropic(max_retries=8), args.model, args.effort, args.attempts)
    cached = CachedExtractionModel(model, args.cache, PROMPT_VERSION)
    extract = ExtractArticle(reader, cached, JsonExtractStore(args.out / "extract"))
    outcomes = ProcessFolder(extract, args.workers)(selected_pdfs(args), report_outcome)
    failed = [outcome.pdf.stem for outcome in outcomes if outcome.error]
    print(f"готово: {len(outcomes) - len(failed)}, ошибок: {len(failed)} {' '.join(failed)}".rstrip())
    return 1 if failed else 0


def report_outcome(outcome: Outcome) -> None:
    if outcome.error:
        print(f"{outcome.pdf.stem}: ошибка {type(outcome.error).__name__}: {outcome.error}", file=sys.stderr)
        return
    result = outcome.result
    print(
        f"{outcome.pdf.stem}: in_scope={result.publication.in_scope} материалов {len(result.materials)}, "
        f"приборов {len(result.devices)}, измерений {len(result.measurements)}, упоминаний {len(result.mentions)}"
    )


def main() -> int:
    args = parse_args()
    if args.command == "pages":
        return run_pages(args)
    return run_extract(args)


if __name__ == "__main__":
    sys.exit(main())
