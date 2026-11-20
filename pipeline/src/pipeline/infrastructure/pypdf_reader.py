import logging
from pathlib import Path

import pypdf

from pipeline.domain.article import Article, Page

logging.getLogger("pypdf").setLevel(logging.ERROR)


class PypdfArticleReader:
    parser = f"pypdf {pypdf.__version__}"

    def read(self, pdf: Path) -> Article:
        content = pdf.read_bytes()
        reader = pypdf.PdfReader(pdf)
        pages = tuple(Page(number, page.extract_text() or "") for number, page in enumerate(reader.pages, 1))
        return Article(pdf.stem, content, pages, self.parser)
