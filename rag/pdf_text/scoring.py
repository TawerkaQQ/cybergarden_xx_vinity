import unicodedata
from collections import Counter
from collections.abc import Iterable
from dataclasses import dataclass


def characters(texts: Iterable[str], *, digits: bool = False) -> Counter[str]:
    return Counter(
        character
        for text in texts
        for character in unicodedata.normalize("NFC", text)
        if (character.isdecimal() if digits else character.isprintable() and not character.isspace())
    )


@dataclass(frozen=True)
class Counts:
    matched: int
    extracted: int
    reference: int

    def __add__(self, other: "Counts") -> "Counts":
        return Counts(
            self.matched + other.matched,
            self.extracted + other.extracted,
            self.reference + other.reference,
        )

    def report(self) -> dict:
        return {
            "matched": self.matched,
            "extracted": self.extracted,
            "reference": self.reference,
            "extra": self.extracted - self.matched,
            "missing": self.reference - self.matched,
            "precision": self.matched / self.extracted if self.extracted else None,
            "recall": self.matched / self.reference if self.reference else None,
            "f1": 2 * self.matched / (self.extracted + self.reference)
            if self.extracted + self.reference else None,
        }


def compare(extracted: Counter[str], reference: Counter[str]) -> Counts:
    return Counts((extracted & reference).total(), extracted.total(), reference.total())


def differences(extracted: Counter[str], reference: Counter[str]) -> dict:
    return {
        "extra": dict((extracted - reference).most_common(12)),
        "missing": dict((reference - extracted).most_common(12)),
    }
