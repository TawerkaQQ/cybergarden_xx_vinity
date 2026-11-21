import unicodedata
from pathlib import Path

import pymupdf

from pipeline.domain.highlight import Box, Highlight

MIN_WORDS = 3
PADDING = 2.0


class PyMuPdfGeometry:
    def __init__(self, pdfs: dict[str, Path]):
        self._pdfs = pdfs
        self._documents: dict[str, pymupdf.Document] = {}

    def page_sizes(self, publication_id: str) -> dict[int, tuple[float, float]]:
        document = self._document(publication_id)
        if document is None:
            return {}
        return {number: (round(page.rect.width, 2), round(page.rect.height, 2)) for number, page in enumerate(document, 1)}

    def locate(self, publication_id: str, page: int, fragment: str, value: str) -> Highlight | None:
        document = self._document(publication_id)
        if document is None or not 1 <= page <= len(document):
            return None
        pdf_page = document[page - 1]
        boxes, exact = fragment_rects(pdf_page, fragment)
        value_boxes = value_rects(pdf_page, value, boxes)
        if not boxes and not value_boxes:
            return None
        return Highlight(page, fragment, tuple(map(as_box, boxes or value_boxes)), tuple(map(as_box, value_boxes)), exact)

    def _document(self, publication_id: str) -> pymupdf.Document | None:
        if publication_id not in self._documents:
            path = self._pdfs.get(publication_id)
            if path is None or not path.exists():
                return None
            self._documents[publication_id] = pymupdf.open(path)
        return self._documents[publication_id]


def fragment_rects(page: pymupdf.Page, fragment: str) -> tuple[list[pymupdf.Rect], bool]:
    candidates = dict.fromkeys([fragment, unicodedata.normalize("NFKC", fragment), fragment.replace("- ", "")])
    for candidate in candidates:
        if candidate.strip():
            rects = page.search_for(candidate)
            if rects:
                return rects, True
    words = fragment.split()
    for length in range(len(words) - 1, MIN_WORDS - 1, -1):
        for start in dict.fromkeys((0, len(words) - length)):
            rects = page.search_for(" ".join(words[start:start + length]))
            if rects:
                return rects, False
    return [], False


def value_rects(page: pymupdf.Page, value: str, around: list[pymupdf.Rect]) -> list[pymupdf.Rect]:
    value = (value or "").strip()
    if not value:
        return []
    if around:
        area = pymupdf.Rect(around[0])
        for rect in around[1:]:
            area |= rect
        for clip in (area + (-PADDING, -PADDING, PADDING, PADDING), pymupdf.Rect(0, area.y0 - PADDING, page.rect.width, area.y1 + PADDING)):
            rects = page.search_for(value, clip=clip)
            if rects:
                return rects
        return []
    rects = page.search_for(value)
    return rects if len(rects) == 1 else []


def as_box(rect: pymupdf.Rect) -> Box:
    return (round(rect.x0, 2), round(rect.y0, 2), round(rect.x1, 2), round(rect.y1, 2))
