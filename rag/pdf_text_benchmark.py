import argparse
import json
import shutil
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import asdict
from pathlib import Path

import pypdf

from config import DATA_DIR
from evaluation.files import write_json
from pdf_text.reader import (
    DENSE_GRAPHICS_DPI,
    DENSE_GRAPHICS_MODE,
    MIN_GRAPHICS_CONFIDENCE,
    SCAN_DPI,
    SCAN_MODE,
    SPARSE_GRAPHICS_DPI,
    SPARSE_GRAPHICS_MODE,
    PdfText,
    engine_counts,
    execute,
    extract_pdf,
    poppler_text,
    sha256,
)
from pdf_text.scoring import Counts, characters, compare, differences

WORK_DIR = Path(__file__).resolve().parent / ".pdf-text"


def main() -> None:
    parser = argparse.ArgumentParser(description="Посимвольная оценка извлечения текста PDF")
    parser.add_argument("--pdf-dir", type=Path, default=DATA_DIR / "dev")
    parser.add_argument("--limit", type=int, default=5, help="первые N PDF в порядке имён")
    parser.add_argument("--reference-pdfs", type=Path, default=WORK_DIR / "reference-pdf")
    parser.add_argument("--graphics-reference", type=Path, help="JSON с ручной расшифровкой рисунков по страницам")
    parser.add_argument("--out", type=Path, default=WORK_DIR / "d001-d005")
    parser.add_argument("--tesseract", default=shutil.which("tesseract") or str(WORK_DIR / "tools/bin/tesseract"))
    parser.add_argument("--language", default="eng")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--text-only", action="store_true", help="пропустить OCR рисунков на текстовых страницах")
    args = parser.parse_args()
    if args.limit < 1 or args.workers < 1:
        parser.error("--limit и --workers должны быть больше нуля")
    if args.text_only and args.graphics_reference:
        parser.error("--graphics-reference требует распознавания рисунков")
    try:
        run(args)
    except (OSError, ValueError) as error:
        parser.exit(1, f"{error}\n")


def run(args: argparse.Namespace) -> None:
    pdfs = sorted(args.pdf_dir.glob("*.pdf"))[:args.limit]
    if len(pdfs) < args.limit:
        raise ValueError(f"В папке {args.pdf_dir} меньше {args.limit} PDF")
    version = execute([args.tesseract, "--version"]).decode().splitlines()[0]
    graphics_reference = (
        json.loads(args.graphics_reference.read_text(encoding="utf-8")) if args.graphics_reference else {}
    )
    if not isinstance(graphics_reference, dict) or any(
        not isinstance(pages, dict) for pages in graphics_reference.values()
    ):
        raise ValueError("Эталон OCR рисунков должен сопоставлять публикации со страницами и текстом")

    articles = {}
    totals = {"characters": Counts(0, 0, 0), "digits": Counts(0, 0, 0)}
    with ThreadPoolExecutor(max_workers=args.workers) as executor:
        tasks = {executor.submit(evaluate_article, pdf, args): pdf.stem for pdf in pdfs}
        for task in as_completed(tasks):
            name = tasks[task]
            report, counts, extraction = task.result()
            if name in graphics_reference:
                report["graphics_samples"] = score_graphics(extraction, graphics_reference[name])
            articles[name] = report
            totals = {key: totals[key] + counts[key] for key in totals}
            print_summary(name, report["characters"], report["pages_by_engine"], report["graphics_pages"])
            for sample in report.get("graphics_samples", []):
                print_summary(f"{name} OCR рисунков, стр. {sample['page']}", sample)

    write_json(args.out / "report.json", {
        "comparison": "NFC, printable non-whitespace character multiset per PDF",
        "tools": {
            "pypdf": pypdf.__version__,
            "ocr": version,
            "ocr_language": args.language,
            "ocr_scan_dpi": SCAN_DPI,
            "ocr_scan_psm": SCAN_MODE,
            "ocr_graphics_modes": None if args.text_only else [
                {"dpi": SPARSE_GRAPHICS_DPI, "psm": SPARSE_GRAPHICS_MODE},
                {"dpi": DENSE_GRAPHICS_DPI, "psm": DENSE_GRAPHICS_MODE},
            ],
            "ocr_graphics_min_confidence": None if args.text_only else MIN_GRAPHICS_CONFIDENCE,
            "reference_text": "Poppler pdftotext -layout",
        },
        "total": {key: value.report() for key, value in totals.items()},
        "articles": {name: articles[name] for name in sorted(articles)},
    })
    print_summary("ВСЕ", totals["characters"].report())


def evaluate_article(pdf: Path, args: argparse.Namespace) -> tuple[dict, dict[str, Counts], PdfText]:
    reference_pdf = args.reference_pdfs / pdf.name
    if not reference_pdf.is_file():
        reference_pdf = pdf
    extraction = extract_pdf(pdf, args.tesseract, args.language, graphics=not args.text_only)
    reference = poppler_text(reference_pdf, len(extraction.pages))
    if not any(text.strip() for text in reference):
        raise ValueError(f"{pdf.name}: для оценки скана нужен исходный PDF в --reference-pdfs")
    for page, text in zip(extraction.pages, reference, strict=True):
        if page.engine == "tesseract" and page.text.strip() and not text.strip():
            raise ValueError(f"{pdf.name}, стр. {page.page}: для оценки OCR нужен независимый текст")

    predicted = [page.text for page in extraction.pages]
    extracted_counts, reference_counts = characters(predicted), characters(reference)
    counts = {
        "characters": compare(extracted_counts, reference_counts),
        "digits": compare(characters(predicted, digits=True), characters(reference, digits=True)),
    }
    pages = [
        {
            "page": page.page,
            "engine": page.engine,
            **compare(characters([page.text]), characters([text])).report(),
            "graphics_characters": characters([page.graphics_text]).total(),
            "graphics_dpi": page.graphics_dpi,
        }
        for page, text in zip(extraction.pages, reference, strict=True)
    ]
    actual_sha = extraction.sha256
    reference_sha = sha256(reference_pdf)
    write_json(args.out / "extracted" / f"{pdf.stem}.json", {
        "publication_id": pdf.stem,
        "source_sha256": actual_sha,
        "pages": [asdict(page) for page in extraction.pages],
    })
    write_json(args.out / "reference" / f"{pdf.stem}.json", {
        "publication_id": pdf.stem,
        "source_sha256": reference_sha,
        "parser": "Poppler pdftotext -layout",
        "pages": [{"page": number, "text": text} for number, text in enumerate(reference, 1)],
    })
    return {
        "input_sha256": actual_sha,
        "reference_sha256": reference_sha,
        "pages_by_engine": engine_counts(extraction),
        "graphics_pages": sum(bool(page.graphics_text) for page in extraction.pages),
        "graphics_characters": sum(page["graphics_characters"] for page in pages),
        **{key: score.report() for key, score in counts.items()},
        "differences": differences(extracted_counts, reference_counts),
        "pages": pages,
    }, counts, extraction


def score_graphics(extraction: PdfText, reference: dict[str, str]) -> list[dict]:
    samples = []
    pages = {page.page: page for page in extraction.pages}
    for key, text in reference.items():
        number = int(key)
        if number not in pages or pages[number].engine != "pypdf" or not isinstance(text, str):
            raise ValueError(f"Неподходящая страница для сравнения OCR рисунков: {key}")
        page = pages[number]
        extracted, expected = characters([page.graphics_text]), characters([text])
        samples.append({"page": number, **compare(extracted, expected).report(),
                        "differences": differences(extracted, expected)})
    return sorted(samples, key=lambda sample: sample["page"])


def print_summary(name: str, metrics: dict, engines: dict | None = None, graphics_pages: int = 0) -> None:
    parts = [f"{name}: {metrics['matched']} / {metrics['reference']} символов"]
    for key, label in (("precision", "точность"), ("recall", "полнота"), ("f1", "F1")):
        value = metrics[key]
        if value is not None:
            parts.append(f"{label} {value:.2%}")
    if engines is not None:
        parts.extend(f"{engine} {count} стр." for engine, count in engines.items())
        parts.append(f"OCR рисунков {graphics_pages} стр.")
    print(", ".join(parts), flush=True)


if __name__ == "__main__":
    main()
