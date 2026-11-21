import csv
import hashlib
import os
import subprocess
from collections import Counter
from dataclasses import dataclass
from io import StringIO
from pathlib import Path
from tempfile import TemporaryDirectory

from pypdf import PdfReader, PdfWriter

SCAN_DPI, SCAN_MODE = 300, 3
SPARSE_GRAPHICS_DPI, SPARSE_GRAPHICS_MODE = 300, 11
DENSE_GRAPHICS_DPI, DENSE_GRAPHICS_MODE = 450, 3
MIN_GRAPHICS_CONFIDENCE = 70
MIN_RASTER_SIDE = 300


@dataclass(frozen=True)
class PageText:
    page: int
    engine: str
    text: str
    graphics_text: str
    graphics_dpi: int | None = None


@dataclass(frozen=True)
class PdfText:
    pages: list[PageText]
    sha256: str


def extract_pdf(pdf: Path, tesseract: str, language: str, *, graphics: bool) -> PdfText:
    text_layer = [page.extract_text() or "" for page in PdfReader(pdf).pages]
    with TemporaryDirectory(prefix="vinity-pdf-text-") as directory:
        residual = Path(directory) / "graphics.pdf" if graphics and any(map(has_text, text_layer)) else None
        raster_pages = set()
        if residual is not None:
            write_graphics_pdf(pdf, residual)
            raster_pages = large_raster_pages(pdf)
        pages = [
            extract_page(pdf, number, text, residual, number in raster_pages, tesseract, language)
            for number, text in enumerate(text_layer, 1)
        ]
    return PdfText(pages, sha256(pdf))


def has_text(text: str) -> bool:
    return any(character.isprintable() and not character.isspace() for character in text)


def extract_page(
    pdf: Path, page: int, text: str, graphics: Path | None, has_large_raster: bool, tesseract: str, language: str,
) -> PageText:
    if not has_text(text):
        png = render_page(pdf, page, SCAN_DPI)
        return PageText(page, "tesseract", ocr(png, tesseract, language, SCAN_MODE, dpi=SCAN_DPI), "")

    if graphics:
        visual_text, dpi = read_graphics(graphics, page, has_large_raster, tesseract, language)
        return PageText(page, "pypdf", text, visual_text, dpi if visual_text else None)
    return PageText(page, "pypdf", text, "")


def read_graphics(
    pdf: Path, page: int, has_large_raster: bool, tesseract: str, language: str,
) -> tuple[str, int]:
    sparse = graphics_text(pdf, page, tesseract, language, SPARSE_GRAPHICS_DPI, SPARSE_GRAPHICS_MODE)
    if has_large_raster:
        dense = graphics_text(pdf, page, tesseract, language, DENSE_GRAPHICS_DPI, DENSE_GRAPHICS_MODE)
        if sum(map(str.isalnum, dense)) > sum(map(str.isalnum, sparse)):
            return dense, DENSE_GRAPHICS_DPI
    return sparse, SPARSE_GRAPHICS_DPI


def graphics_text(pdf: Path, page: int, tesseract: str, language: str, dpi: int, mode: int) -> str:
    return confident_lines(ocr(render_page(pdf, page, dpi), tesseract, language, mode, dpi=dpi, tsv=True))


def large_raster_pages(pdf: Path) -> set[int]:
    output = execute(["pdfimages", "-list", str(pdf)]).decode("utf-8")
    pages = set()
    for line in output.splitlines()[2:]:
        fields = line.split()
        if len(fields) >= 5 and fields[2] == "image" and min(int(fields[3]), int(fields[4])) >= MIN_RASTER_SIDE:
            pages.add(int(fields[0]))
    return pages


def write_graphics_pdf(pdf: Path, output: Path) -> None:
    writer = PdfWriter(clone_from=pdf)
    writer.remove_text()
    with output.open("wb") as file:
        writer.write(file)


def render_page(pdf: Path, number: int, dpi: int) -> bytes:
    return execute([
        "pdftoppm", "-f", str(number), "-l", str(number), "-r", str(dpi),
        "-gray", "-png", "-singlefile", "-hide-annotations", str(pdf),
    ])


def ocr(png: bytes, tesseract: str, language: str, mode: int, *, dpi: int, tsv: bool = False) -> str:
    command = [tesseract, "stdin", "stdout", "-l", language, "--dpi", str(dpi), "--psm", str(mode)]
    if tsv:
        command.append("tsv")
    return execute(command, data=png).decode("utf-8")


def confident_lines(tsv: str) -> str:
    lines: dict[tuple[str, ...], list[str]] = {}
    for row in csv.DictReader(StringIO(tsv), delimiter="\t"):
        text = row["text"].strip()
        if row["level"] == "5" and text and float(row["conf"]) >= MIN_GRAPHICS_CONFIDENCE:
            key = tuple(row[column] for column in ("block_num", "par_num", "line_num"))
            lines.setdefault(key, []).append(text)
    return "\n".join(
        " ".join(words) for words in lines.values() if any(character.isalnum() for word in words for character in word)
    )


def poppler_text(pdf: Path, expected_pages: int) -> list[str]:
    count = len(PdfReader(pdf).pages)
    if count != expected_pages:
        raise ValueError(f"{pdf.name}: в эталонном PDF {count} страниц, ожидалось {expected_pages}")
    return [
        execute([
            "pdftotext", "-f", str(page), "-l", str(page), "-nopgbrk", "-layout", "-enc", "UTF-8", str(pdf), "-",
        ]).decode("utf-8")
        for page in range(1, expected_pages + 1)
    ]


def execute(command: list[str], *, data: bytes | None = None) -> bytes:
    env = os.environ.copy()
    env.setdefault("OMP_THREAD_LIMIT", "1")
    try:
        result = subprocess.run(command, input=data, capture_output=True, timeout=150, env=env)
    except FileNotFoundError as error:
        raise ValueError(f"Команда не найдена: {command[0]}") from error
    except subprocess.TimeoutExpired as error:
        raise ValueError(f"{Path(command[0]).name}: превышено время ожидания") from error
    if result.returncode:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise ValueError(f"{Path(command[0]).name}: {detail or f'код {result.returncode}'}")
    return result.stdout


def sha256(pdf: Path) -> str:
    with pdf.open("rb") as file:
        return hashlib.file_digest(file, "sha256").hexdigest()


def engine_counts(pdf: PdfText) -> dict[str, int]:
    return dict(Counter(page.engine for page in pdf.pages))
