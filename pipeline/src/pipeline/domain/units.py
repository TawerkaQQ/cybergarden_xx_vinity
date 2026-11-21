import re
import unicodedata
from dataclasses import dataclass

from pipeline.domain.profile import Profile

SQUASHED = re.compile(r"[\s*·⋅]+")
LETTERS = re.compile(r"[A-Za-z]+")


@dataclass(frozen=True)
class Converted:
    value: float | None
    unit: str
    recognized: bool


def squash(unit: str) -> str:
    text = unicodedata.normalize("NFKC", unit).replace("−", "-").replace("–", "-").replace("Ω", "Ohm").replace("μ", "u")
    return SQUASHED.sub("", text)


class UnitBook:
    def __init__(self, profile: Profile):
        known = {alias: canonical for alias, canonical in profile.unit_aliases.items()}
        for conversion in profile.conversions:
            known.setdefault(conversion.source, conversion.source)
            known.setdefault(conversion.target, conversion.target)
        for target in profile.values.metrics.values():
            known.setdefault(target, target)
        for table in profile.tables:
            for unit in table.units.values():
                known.setdefault(unit.target, unit.target)
        self._canonical = {squash(alias): canonical for alias, canonical in known.items()}
        self._canonical.update({squash(canonical): canonical for canonical in known.values()})
        self._factors = {(conversion.source, conversion.target): conversion.factor for conversion in profile.conversions}

    def canonical(self, unit: str | None) -> str:
        if not unit:
            return ""
        return self._canonical.get(squash(unit), unit.strip())

    def convert(self, value: float | None, unit: str | None, target: str | None) -> Converted:
        source = self.canonical(unit)
        if value is None:
            return Converted(None, "", True)
        if not target or source == target:
            return Converted(value, source or target or "", True)
        if not source:
            return Converted(value, target, True)
        factor = self._factors.get((source, target))
        if factor is not None:
            return Converted(value * factor, target, True)
        target_letters = leading_letters(target)
        if squash(source) not in self._canonical and target_letters and leading_letters(source).startswith(target_letters):
            return Converted(value, target, False)
        return Converted(None, "", False)


def leading_letters(unit: str) -> str:
    match = LETTERS.match(squash(unit))
    return match.group() if match else ""
