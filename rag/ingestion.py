from collections.abc import Iterable
from pathlib import Path

from pypdf import PdfReader

from config import DATA_DIR
from evidence import Fragment

CHUNK_SIZE = 1000
CHUNK_OVERLAP = 200


def split_text(text: str) -> Iterable[str]:
    start = 0
    while start < len(text):
        end = min(start + CHUNK_SIZE, len(text))
        if end < len(text):
            boundary = text.rfind(" ", start + CHUNK_SIZE // 2, end)
            if boundary != -1:
                end = boundary
        yield text[start:end]
        if end == len(text):
            break
        start = end - CHUNK_OVERLAP


def load_pdfs_from_data_dir(directory: Path = DATA_DIR) -> Iterable[Fragment]:
    pdf_files = sorted(directory.rglob("*.pdf"))
    if not pdf_files:
        raise FileNotFoundError(f"В директории {directory} не найдено PDF-файлов")

    for pdf_path in pdf_files:
        source = pdf_path.relative_to(directory).as_posix()
        for page_number, page in enumerate(PdfReader(pdf_path).pages, start=1):
            text = " ".join(page.extract_text().split())
            for chunk in split_text(text):
                yield Fragment(source, page_number, chunk, publication_id=pdf_path.stem)
