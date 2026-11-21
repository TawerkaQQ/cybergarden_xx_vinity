import re
from dataclasses import dataclass
from unicodedata import normalize

PUBLICATION_ID = re.compile(r"\b[DP]\d{3}\b", re.IGNORECASE)
LOCATOR = re.compile(
    r"\b(table|табл(?:иц\w*)?\.?|figure|fig\.?|рис(?:ун\w*)?\.?)\s*(?:№\s*)?([a-z]?\d+[a-z]?|[ivxlcdm]+)\b",
    re.IGNORECASE,
)


def publication_ids(text: str) -> list[str]:
    return list(dict.fromkeys(value.upper() for value in PUBLICATION_ID.findall(text)))


def source_locations(text: str) -> list[str]:
    locations = []
    for prefix, number in LOCATOR.findall(text):
        kind = "table" if prefix.lower().startswith(("table", "табл")) else "figure"
        locations.append(f"{kind}:{number.casefold()}")
    return list(dict.fromkeys(locations))


def normalize_label(text: str) -> str:
    return " ".join(normalize("NFKC", text).casefold().split())


@dataclass(frozen=True)
class Fragment:
    source: str
    page: int | None
    text: str
    keywords: str = ""
    publication_id: str = ""
    record_id: str = ""
    locator: str = ""
    search_labels: tuple[tuple[str, str], ...] = ()

    @property
    def reference(self) -> str:
        return f"{self.source}, стр. {self.page}" if self.page is not None else self.source


@dataclass(frozen=True)
class WebPassage:
    title: str
    url: str
    text: str
    fetched_at: str

    @property
    def reference(self) -> str:
        return f"{self.title} — {self.url} (получено {self.fetched_at})"
