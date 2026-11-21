from dataclasses import dataclass


@dataclass(frozen=True)
class Fragment:
    source: str
    page: int | None
    text: str
    keywords: str = ""

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
