import logging
from pathlib import Path

import pypdf

from pipeline.domain.article import Article, Page

logging.getLogger("pypdf").setLevel(logging.ERROR)


class PypdfArticleReader:
    def read(self, pdf: Path) -> Article:
        reader = pypdf.PdfReader(pdf)
        pages = tuple(Page(number, valid_text(page.extract_text() or "")) for number, page in enumerate(reader.pages, 1))
        return Article(pdf.stem, pdf.read_bytes(), pages, "pypdf", pypdf.__version__)


def valid_text(text: str) -> str:
    return text.encode("utf-16", "surrogatepass").decode("utf-16", "replace")
