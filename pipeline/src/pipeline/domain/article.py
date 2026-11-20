from dataclasses import dataclass


@dataclass(frozen=True)
class Page:
    number: int
    text: str


@dataclass(frozen=True)
class Article:
    publication_id: str
    content: bytes
    pages: tuple[Page, ...]
    text_variant: str
    tool_version: str
