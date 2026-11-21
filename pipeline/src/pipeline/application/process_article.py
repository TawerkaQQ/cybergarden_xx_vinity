from pathlib import Path
from typing import Any

from pipeline.application.build_pages import BuildPages
from pipeline.application.extract_article import ExtractArticle
from pipeline.application.folder import article_pdfs
from pipeline.application.publish_set import PublishSet
from pipeline.domain.ports import ReportStore


class ProcessArticle:
    def __init__(self, build_pages: BuildPages, extract: ExtractArticle, publish: PublishSet, reports: ReportStore):
        self._build_pages = build_pages
        self._extract = extract
        self._publish = publish
        self._reports = reports

    def __call__(self, pdf: Path, directory: Path) -> dict[str, Any]:
        self._build_pages(pdf)
        self._extract(pdf)
        self._publish([other.stem for other in article_pdfs(pdf.parent)], directory)
        return self._reports.read(directory, pdf.stem)
