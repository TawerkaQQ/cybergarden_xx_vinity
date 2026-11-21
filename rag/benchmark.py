import argparse
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from chat_client import complete as service_complete
from config import DATA_DIR, get_chat_settings
from csv_corpus import read_schema
from evaluation import rules
from evaluation.files import read_observations, read_results, write_json, write_results
from evaluation.pdf_input import PdfInput, default_tesseract
from evaluation.runner import PROMPT_VERSION, TABLE_PROMPT_VERSION, ArticleRun, extract_article
from evaluation.scoring import build_report, parse_values

BENCHMARK_DIR = Path(__file__).resolve().parent / ".benchmark"
DEFAULT_GOLD = DATA_DIR / "dev_gold" / "results.csv"


def main() -> int:
    parser = argparse.ArgumentParser(description="Оценка извлечения числовых результатов RAG")
    commands = parser.add_subparsers(dest="command", required=True)
    run = commands.add_parser("run", help="извлечь результаты из PDF и сравнить с эталоном")
    run.add_argument("--pdf-dir", type=Path, default=DATA_DIR / "dev")
    run.add_argument("--out", type=Path)
    run.add_argument("--cache", type=Path, default=BENCHMARK_DIR / "cache")
    run.add_argument("--pdf-cache", type=Path, default=BENCHMARK_DIR / "pdf-pages")
    run.add_argument("--pdf-workers", type=positive_int, default=6)
    run.add_argument("--tesseract", default=default_tesseract())
    run.add_argument("--ocr-language", default="eng")
    run.add_argument("--workers", type=positive_int)
    run.add_argument("--extractor", choices=("service", "rules"), default="service")
    run.add_argument("--base-results", type=Path, help="дополнить таблицами готовый results.csv бенчмарка")
    score = commands.add_parser("score", help="сравнить готовый results.csv с эталоном")
    score.add_argument("predictions", type=Path)
    score.add_argument("--json", type=Path, help="сохранить отчёт")
    for command in (run, score):
        command.add_argument("--gold", type=Path, default=DEFAULT_GOLD)
        command.add_argument("--only", nargs="+", metavar="ID")
    args = parser.parse_args()
    try:
        if args.command == "run":
            return run_benchmark(args)
        summarize(args.predictions, args.gold, set(args.only or []), args.json)
    except (OSError, ValueError) as error:
        parser.exit(1, f"{error}\n")
    return 0


def positive_int(text: str) -> int:
    number = int(text)
    if number < 1:
        raise argparse.ArgumentTypeError("ожидается целое число больше нуля")
    return number


def run_benchmark(args: argparse.Namespace) -> int:
    pdfs = {path.stem: path for path in sorted(args.pdf_dir.glob("*.pdf"))}
    selected = set(args.only or pdfs)
    if missing := selected - pdfs.keys():
        raise ValueError(f"Нет PDF: {', '.join(sorted(missing))}")
    if not selected:
        raise ValueError(f"В {args.pdf_dir} не найдено PDF")
    columns = read_schema()["tables"]["results"]

    if args.extractor == "service":
        profile = get_chat_settings().profile
        complete = service_complete
        backend = "http"
    else:
        profile, complete, backend = "", None, None

    if args.base_results and not complete:
        raise ValueError("--base-results используется с извлекателем service")
    previous = read_observations(args.base_results) if args.base_results else None
    version = f"{PROMPT_VERSION}+{TABLE_PROMPT_VERSION}" if complete else rules.VERSION
    output = args.out or BENCHMARK_DIR / f"dev-{args.extractor}"
    workers = args.workers or 4
    print(f"PDF: {len(selected)}, извлечение: {args.extractor}, версия: {version}", flush=True)
    runs = {}
    reader = PdfInput(args.pdf_cache, args.tesseract, args.ocr_language)
    documents = {}
    with ThreadPoolExecutor(max_workers=args.pdf_workers) as executor:
        tasks = {executor.submit(reader.read, pdfs[name]): name for name in sorted(selected)}
        for task in as_completed(tasks):
            name = tasks[task]
            try:
                documents[name] = task.result()
            except Exception as error:
                detail = f"Ошибка чтения PDF ({type(error).__name__}): {error}"
                runs[name] = ArticleRun(name, failed_batches=[{"pages": [], "error": detail}])
                print(f"  {name}: {detail}", file=sys.stderr, flush=True)
    print(f"Текст подготовлен для {len(documents)}/{len(selected)} PDF", flush=True)
    with ThreadPoolExecutor(max_workers=workers) as executor:
        tasks = {
            executor.submit(
                extract_article, name, documents[name], args.cache, complete, profile,
                previous.get(name, []) if previous is not None else None,
            ): name
            for name in sorted(documents)
        }
        for task in as_completed(tasks):
            name = tasks[task]
            try:
                run = task.result()
            except Exception as error:
                run = ArticleRun(name, failed_batches=[{"pages": [], "error": type(error).__name__}])
            runs[name] = run
            print(
                f"{name}: {len(run.observations)} значений, страниц с текстом {run.text_pages}/{run.total_pages}, "
                f"OCR скана {run.ocr_pages}, OCR рисунков {run.graphics_pages}, "
                f"ячеек таблиц {run.table_cells}, заменено {run.table_replaced}, "
                f"пакетов с ошибкой {len(run.failed_batches)}",
                flush=True,
            )
            for failure in run.failed_batches:
                print(f"  {name} {failure['pages']}: {failure['error']}", file=sys.stderr, flush=True)

    predictions = output / "results.csv"
    write_results(predictions, columns, ((name, runs[name].observations) for name in sorted(runs)))
    failed = sum(len(run.failed_batches) for run in runs.values())
    write_json(output / "run.json", {
        "extractor": args.extractor,
        "version": version,
        "profile": profile or None,
        "backend": backend,
        "pdf_reader": reader.version,
        "pdf_cache": str(reader.cache),
        "base_results": str(args.base_results) if args.base_results else None,
        "failed_batches": failed,
        "articles": {name: runs[name].summary() for name in sorted(runs)},
    })
    if failed:
        print(f"Прогон неполный: пакетов с ошибкой {failed}", file=sys.stderr)
    summarize(predictions, args.gold, selected, output / "scores.json")
    return int(failed > 0)


def summarize(prediction_file: Path, gold_file: Path, selected: set[str], report_file: Path | None) -> None:
    gold_rows = read_results(gold_file)
    predicted_rows = read_results(prediction_file)
    gold_ids = {row["publication_id"] for row in gold_rows}
    if missing := selected - gold_ids:
        raise ValueError(f"Нет публикаций в эталоне: {', '.join(sorted(missing))}")
    publication_ids = selected or gold_ids | {row["publication_id"] for row in predicted_rows}
    if not publication_ids:
        raise ValueError("Нет публикаций для сравнения")
    gold = parse_values([row for row in gold_rows if row["publication_id"] in publication_ids])
    predicted = parse_values([row for row in predicted_rows if row["publication_id"] in publication_ids])
    report = build_report(predicted, gold, publication_ids)
    if report_file:
        write_json(report_file, report)
    for mode in ("strict", "loose"):
        print(f"micro {mode:6} {format_score(report['micro'][mode])}")
        print(f"macro {mode:6} {format_rates(report['macro'][mode])}")
    for name, scores in report["by_publication"].items():
        print(f"{name} strict   {format_score(scores['strict'])}")
    print("Совпадения с учётом типа источника")
    labels = {"table": "таблицы", "figure": "рисунки", "text": "текст"}
    for name, scores in report["by_source"].items():
        print(f"{labels[name]:7} strict  {format_score(scores['strict'])}")


def format_rates(score: dict) -> str:
    return f"точность {score['precision']:7.2%}, полнота {score['recall']:7.2%}, F1 {score['f1']:7.2%}"


def format_score(score: dict) -> str:
    return f"{format_rates(score)}  TP {score['tp']}, FP {score['fp']}, FN {score['fn']}"


if __name__ == "__main__":
    sys.exit(main())
