import json
import shutil
from dataclasses import asdict
from pathlib import Path

from pypdf import __version__ as pypdf_version

from evaluation.files import digest, write_json
from pdf_text.reader import PageText, PdfText, execute, extract_pdf, sha256

PAGE_VERSION = "pypdf-tesseract-v1"
LOCAL_TESSERACT = Path(__file__).resolve().parents[1] / ".pdf-text" / "tools" / "bin" / "tesseract"


def default_tesseract() -> str:
    return shutil.which("tesseract") or str(LOCAL_TESSERACT)


class PdfInput:
    def __init__(self, cache: Path, tesseract: str, language: str):
        self.tesseract = tesseract
        self.language = language
        version = execute([tesseract, "--version"]).decode().splitlines()[0]
        self.version = f"{PAGE_VERSION}; pypdf {pypdf_version}; {version}; {language}"
        self.cache = cache / digest(self.version)[:12]

    def cache_path(self, pdf: Path, source_hash: str) -> Path:
        return self.cache / f"{pdf.stem}-{source_hash[:12]}.json"

    def read(self, pdf: Path) -> PdfText:
        source_hash = sha256(pdf)
        path = self.cache_path(pdf, source_hash)
        if path.is_file():
            try:
                stored = json.loads(path.read_text(encoding="utf-8"))
                if stored["source_sha256"] == source_hash and stored["version"] == self.version:
                    return PdfText([PageText(**row) for row in stored["pages"]], source_hash)
            except (KeyError, TypeError, ValueError):
                pass

        extracted = extract_pdf(pdf, self.tesseract, self.language, graphics=True)
        self.save(pdf, extracted)
        return extracted

    def save(self, pdf: Path, extracted: PdfText) -> None:
        write_json(self.cache_path(pdf, extracted.sha256), {
            "publication_id": pdf.stem,
            "source_sha256": extracted.sha256,
            "version": self.version,
            "pages": [asdict(page) for page in extracted.pages],
        })
