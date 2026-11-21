import argparse
import re
import sys
from pathlib import Path

import openai

from pipeline.application.build_pages import BuildPages
from pipeline.application.extract_article import ExtractArticle
from pipeline.application.folder import article_pdfs
from pipeline.application.process_article import ProcessArticle
from pipeline.application.process_folder import Outcome, ProcessFolder
from pipeline.application.profile_extraction import ProfileExtraction
from pipeline.application.publish_set import PublishSet
from pipeline.application.score_set import ScoreSet
from pipeline.domain.assembly import Assembler
from pipeline.domain.extraction_shape import ExtractionShape
from pipeline.domain.prompts import PromptBook
from pipeline.domain.scoring import Score, Scorer
from pipeline.infrastructure.cached_model import CachedExtractionModel
from pipeline.infrastructure.csv_tables import CsvTables
from pipeline.infrastructure.extract_store import JsonExtractStore
from pipeline.infrastructure.openai_model import OpenAiCompatibleModel
from pipeline.infrastructure.pages_store import JsonPagesStore
from pipeline.infrastructure.profile_files import load_profile
from pipeline.infrastructure.pymupdf_geometry import PyMuPdfGeometry
from pipeline.infrastructure.pypdf_reader import PypdfArticleReader
from pipeline.infrastructure.report_files import JsonReportStore
from pipeline.infrastructure.work_files import WorkFiles

DEFAULT_CACHE = Path(__file__).resolve().parents[2] / ".cache"
DEFAULT_MODEL = "qwen3.6-35b-a3b"
REQUEST_TIMEOUT_SECONDS = 1800


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(prog="python -m pipeline")
    commands = parser.add_subparsers(dest="command", required=True)
    pages = commands.add_parser("pages", help="текст страниц PDF в work/<профиль>/pages")
    extract = commands.add_parser("extract", help="извлечение моделью в work/<профиль>/extract")
    assemble = commands.add_parser("assemble", help="таблицы, подсветка и JSON статей в out/<профиль>/<набор>")
    score = commands.add_parser("score", help="точность и полнота против data/<профиль>/<набор>_gold")
    run = commands.add_parser("run", help="вся цепочка: pages, extract, assemble и score, если есть эталон")
    article = commands.add_parser("article", help="одна статья: текст, извлечение, сборка набора её папки, JSON статьи")
    article.add_argument("pdf", type=Path)
    for command in (pages, extract, assemble, score, run, article):
        command.add_argument("--profile", required=True)
        command.add_argument("--work", type=Path, default=Path("work"))
        command.add_argument("--profiles", type=Path, default=Path("profiles"))
    for command in (pages, extract, assemble, score, run):
        command.add_argument("--set", dest="sets", nargs="+", required=True)
        command.add_argument("--only", nargs="*")
        command.add_argument("--data", type=Path, default=Path("data"))
    for command in (extract, run, article):
        command.add_argument("--base-url", help="адрес API vLLM, по умолчанию OPENAI_BASE_URL")
        command.add_argument("--model", default=DEFAULT_MODEL)
        command.add_argument("--max-tokens", type=int, default=24576)
        command.add_argument("--thinking", action="store_true")
        command.add_argument("--workers", type=int, default=4)
        command.add_argument("--attempts", type=int, default=3)
        command.add_argument("--runs", type=int, default=1, help="сколько раз извлекать значения; остаются найденные большинством")
        command.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    for command in (assemble, score, run, article):
        command.add_argument("--out", type=Path, default=Path("out"))
    for command in (score, run):
        command.add_argument("--details", action="store_true", help="оценка по каждой статье")
    return parser.parse_args()


def selected_pdfs(args: argparse.Namespace) -> list[Path]:
    only = set(args.only) if args.only else None
    return [pdf for name in args.sets for pdf in article_pdfs(args.data / args.profile / name, only)]


def build_pages(args: argparse.Namespace) -> BuildPages:
    return BuildPages(PypdfArticleReader(), JsonPagesStore(args.work / args.profile / "pages"))


def build_extract(args: argparse.Namespace, shape: ExtractionShape) -> ExtractArticle:
    prompts = PromptBook(shape)
    client = openai.OpenAI(base_url=args.base_url, timeout=REQUEST_TIMEOUT_SECONDS, max_retries=5)
    model = OpenAiCompatibleModel(client, args.model, args.max_tokens, args.thinking, args.attempts)
    cache_key = f"{prompts.version}-{slug(args.model)}{'-thinking' if args.thinking else ''}"
    extraction = ProfileExtraction(model, shape, prompts)
    runs = [
        CachedExtractionModel(extraction, args.cache, cache_key if run == 1 else f"{cache_key}-run{run}")
        for run in range(1, args.runs + 1)
    ]
    print(f"профиль {args.profile}, версия промпта {prompts.version}, модель {args.model}, запусков {args.runs}")
    return ExtractArticle(PypdfArticleReader(), runs, shape, prompts.version, JsonExtractStore(args.work / args.profile / "extract"))


def build_publish(args: argparse.Namespace, shape: ExtractionShape, pdfs: list[Path]) -> PublishSet:
    return PublishSet(
        shape.profile,
        Assembler(shape),
        WorkFiles(args.work / args.profile),
        CsvTables(shape.profile),
        PyMuPdfGeometry({pdf.stem: pdf for pdf in pdfs}),
        JsonReportStore(),
    )


def run_pages(args: argparse.Namespace) -> int:
    build = build_pages(args)
    for pdf in selected_pdfs(args):
        print(f"{pdf.stem}: {build(pdf)} стр.")
    return 0


def run_extract(args: argparse.Namespace) -> int:
    extract = build_extract(args, ExtractionShape(load_profile(args.profiles, args.profile)))
    outcomes = ProcessFolder(extract, args.workers)(selected_pdfs(args), report_outcome)
    failed = [outcome.pdf.stem for outcome in outcomes if outcome.error]
    print(f"готово: {len(outcomes) - len(failed)}, ошибок: {len(failed)} {' '.join(failed)}".rstrip())
    return 1 if failed else 0


def run_assemble(args: argparse.Namespace) -> int:
    shape = ExtractionShape(load_profile(args.profiles, args.profile))
    for name in args.sets:
        pdfs = article_pdfs(args.data / args.profile / name, set(args.only or []) or None)
        publish = build_publish(args, shape, pdfs)
        directory = args.out / args.profile / name
        assembled = publish([pdf.stem for pdf in pdfs], directory)
        sizes = ", ".join(f"{table} {len(rows)}" for table, rows in assembled.tables.items())
        notes = ", ".join(f"{reason} {count}" for reason, count in sorted(assembled.report.items()))
        print(f"{directory}: {sizes}")
        print(f"  {notes}")
    return 0


def run_score(args: argparse.Namespace) -> int:
    profile = load_profile(args.profiles, args.profile)
    score = ScoreSet(Scorer(profile), CsvTables(profile))
    for name in args.sets:
        ours, gold = args.out / args.profile / name, args.data / args.profile / f"{name}_gold"
        print(f"{ours} против {gold}")
        for mode in profile.values.match:
            for primary_only in (False, True):
                scores = score(ours, gold, mode, primary_only)
                label = f"{mode}{', без повторов' if primary_only else ''}"
                print(f"  {label:24} {format_score(sum(scores.values(), Score(0, 0, 0)))}")
                if args.details and not primary_only:
                    for publication_id, value in scores.items():
                        print(f"    {publication_id:10} {format_score(value)}")
    return 0


def run_all(args: argparse.Namespace) -> int:
    failed = run_pages(args) or run_extract(args)
    run_assemble(args)
    graded = [name for name in args.sets if (args.data / args.profile / f"{name}_gold").exists()]
    if graded:
        run_score(argparse.Namespace(**{**vars(args), "sets": graded}))
    return failed


def run_article(args: argparse.Namespace) -> int:
    shape = ExtractionShape(load_profile(args.profiles, args.profile))
    process = ProcessArticle(
        build_pages(args), build_extract(args, shape), build_publish(args, shape, article_pdfs(args.pdf.parent)), JsonReportStore()
    )
    directory = args.out / args.profile / args.pdf.parent.name
    report = process(args.pdf, directory)
    values = report["tables"][shape.profile.values.table]
    print(f"{directory / 'articles' / (args.pdf.stem + '.json')}: значений {len(values)}, с подсветкой {len(report['highlights'])}")
    return 0


def format_score(score: Score) -> str:
    return (
        f"точность {score.precision:6.1%} ({score.matched}/{score.ours}), "
        f"полнота {score.recall:6.1%} ({score.matched}/{score.gold}), F1 {score.f1:6.1%}"
    )


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
    commands = {"pages": run_pages, "extract": run_extract, "assemble": run_assemble, "score": run_score, "run": run_all, "article": run_article}
    return commands[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
