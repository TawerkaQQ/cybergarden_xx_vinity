from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class ExtractResult:
    publication_id: str
    profile: str
    prompt_version: str
    publication: dict[str, Any]
    records: dict[str, list[dict[str, Any]]]
    mentions: list[dict[str, Any]]

    @property
    def in_scope(self) -> bool:
        return bool(self.publication.get("in_scope"))

    def to_json(self) -> dict[str, Any]:
        return {
            "publication_id": self.publication_id,
            "profile": self.profile,
            "prompt_version": self.prompt_version,
            "publication": self.publication,
            "records": self.records,
            "mentions": self.mentions,
        }
