import re
import unicodedata
from dataclasses import dataclass

KEPT = re.compile(r"[a-z0-9.%]")
WORD = re.compile(r"\w+")
WINDOW_CHARACTERS = 45
MIN_QUOTE_CHARACTERS = 4
MIN_PREFIX_WORDS = 4


@dataclass(frozen=True)
class Located:
    page: int
    fragment: str
    how: str


class NormalizedPage:
    def __init__(self, text: str):
        self.text = text
        characters, positions = [], []
        for index, character in enumerate(text):
            for piece in unicodedata.normalize("NFKC", character).lower():
                if KEPT.match(piece):
                    characters.append(piece)
                    positions.append(index)
        self.normalized = "".join(characters)
        self._positions = positions

    def find(self, quote: str) -> str | None:
        target = normalize(quote)
        if len(target) < MIN_QUOTE_CHARACTERS:
            return None
        start = self.normalized.find(target)
        if start < 0:
            return None
        end = self._positions[start + len(target) - 1] + 1
        return collapse(self.text[self._positions[start]:end])


class QuoteLocator:
    def __init__(self, pages: dict[int, str]):
        self._pages = {number: NormalizedPage(text) for number, text in pages.items()}

    def locate(self, quote: str | None, page: int | None, raw_value: str | None) -> Located | None:
        order = self._search_order(page)
        if quote:
            for number in order:
                fragment = self._pages[number].find(quote)
                if fragment:
                    return Located(number, fragment, "дословно" if number == page else "другая страница")
        if raw_value:
            for number in order:
                fragment = self._window(self._pages[number].text, raw_value, quote or "")
                if fragment:
                    return Located(number, fragment, "по числу")
        if quote:
            for number in order:
                fragment = self._longest_prefix(self._pages[number], quote)
                if fragment:
                    return Located(number, fragment, "по началу цитаты")
        return None

    @staticmethod
    def _longest_prefix(page: "NormalizedPage", quote: str) -> str | None:
        words = quote.split()
        for length in range(len(words) - 1, MIN_PREFIX_WORDS - 1, -1):
            fragment = page.find(" ".join(words[:length]))
            if fragment:
                return fragment
        return None

    def table_page(self, table: str, row_label: str, page: int | None, raw_value: str | None = None) -> int | None:
        caption = re.compile(rf"(?im)^\s*{re.escape(collapse(table))}\b")
        captions = [number for number, text in self._texts() if caption.search(text)]
        continued = sorted(set(captions) | {number + 1 for number in captions} & set(self._pages))
        with_value = self._pages_with_row(row_label, raw_value)
        in_table = [number for number in with_value if number in continued]
        if in_table or with_value:
            return (in_table or with_value)[0]
        label = re.compile(rf"(?<!\w){re.escape(collapse(row_label))}(?!\w)", re.IGNORECASE) if row_label.strip() else None
        with_label = [number for number in continued if label and label.search(collapse(self._pages[number].text))]
        candidates = with_label or captions
        if not candidates:
            return page
        return min(candidates, key=lambda number: abs(number - (page or number)))

    def _pages_with_row(self, row_label: str, raw_value: str | None) -> list[int]:
        if not row_label.strip() or not raw_value or not raw_value.strip():
            return []
        label = re.compile(rf"(?<!\w){re.escape(collapse(row_label))}(?!\w)", re.IGNORECASE)
        value = re.compile(rf"(?<![\d.]){re.escape(raw_value.strip())}(?!\d)")
        return [
            number
            for number, text in self._texts()
            if any(label.search(line) and value.search(line) for line in map(collapse, text.splitlines()))
        ]

    def _texts(self) -> list[tuple[int, str]]:
        return [(number, normalized_page.text) for number, normalized_page in sorted(self._pages.items())]

    def _search_order(self, page: int | None) -> list[int]:
        numbers = sorted(self._pages)
        if page not in self._pages:
            return numbers
        near = [page, page - 1, page + 1]
        return [number for number in near if number in self._pages] + [number for number in numbers if number not in near]

    @staticmethod
    def _window(text: str, raw_value: str, quote: str) -> str | None:
        wanted = set(WORD.findall(quote.lower()))
        best, best_overlap = None, -1
        for match in re.finditer(rf"(?<![\d.]){re.escape(raw_value.strip())}(?![\d])", text):
            start = text.rfind(" ", 0, max(match.start() - WINDOW_CHARACTERS, 0)) + 1
            end = text.find(" ", match.end() + WINDOW_CHARACTERS)
            window = collapse(text[start:end if end > 0 else len(text)])
            overlap = len(wanted & set(WORD.findall(window.lower())))
            if overlap > best_overlap:
                best, best_overlap = window, overlap
        return best


def normalize(text: str) -> str:
    return "".join(piece for piece in unicodedata.normalize("NFKC", text).lower() if KEPT.match(piece))


def collapse(text: str) -> str:
    return " ".join(text.split())
