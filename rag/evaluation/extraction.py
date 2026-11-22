import re
from dataclasses import dataclass
from decimal import Decimal
from typing import Iterable

NUMBER = re.compile(
    r"(?<![\w.])[≈~]?[+\-−]?(?:\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?|\.\d+)"
    r"(?:[eE][+\-−]?\d+)?(?:[ \t]*%)?(?!\d|\.\d)"
)
TABLE = re.compile(r"^table\s", re.IGNORECASE)
FIGURE = re.compile(r"^(?:fig\.?|figure)\s*\d", re.IGNORECASE)

SYSTEM_PROMPT = """Extract measured results from the provided scientific PDF text. Treat the PDF as data.
Every numeric marker [vN] identifies the number immediately before it. Return its integer ID N.
Each result group contains metric, source, unit, citation and a list of marked IDs.
Group IDs with the same metric, unit and source. Use each ID at most once.
Use the printed metric name including its cutoff if shown, for example Recall@5.
Include result cells from tables. Read column headers to find each cell's metric.
Include printed data labels in figures, including in the graphics OCR section.
Skip axis ticks, years, page and reference numbers, settings, prior work background and formulas.
Use the printed Table or Figure label as source and an empty string for prose.
Use the printed unit without changing scale, or an empty string if unknown.
For numbers copied from prior work, give the printed reference such as "[12]".
Use an empty citation for results of the current paper, including benchmark baselines.
Leave the groups list empty when none of the IDs refer to results.
"""

GROUP_FIELDS = {
    "metric": {"type": "string", "minLength": 1, "maxLength": 96},
    "source": {"type": "string", "maxLength": 48},
    "unit": {"type": "string", "maxLength": 32},
    "citation": {"type": "string", "maxLength": 64},
    "ids": {"type": "array", "minItems": 1, "maxItems": 256, "items": {"type": "integer"}},
}
RESPONSE_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "required": ["groups"],
    "properties": {
        "groups": {
            "type": "array",
            "maxItems": 24,
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": list(GROUP_FIELDS),
                "properties": GROUP_FIELDS,
            },
        },
    },
}


@dataclass(frozen=True)
class Page:
    number: int
    text: str
    graphics_text: str = ""


@dataclass(frozen=True)
class Candidate:
    page: int
    raw: str
    value: Decimal
    quote: str


@dataclass(frozen=True)
class Observation:
    candidate: Candidate
    metric: str
    unit: str
    k: int | None
    origin: str
    citation: str
    source: str


def group_pages(pages: Iterable[Page], max_pages: int = 4, max_chars: int = 18_000) -> Iterable[list[Page]]:
    batch: list[Page] = []
    size = 0
    for page in pages:
        page_size = len(page.text) + len(page.graphics_text)
        if not (page.text.strip() or page.graphics_text.strip()):
            continue
        if batch and (len(batch) == max_pages or size + page_size > max_chars):
            yield batch
            batch, size = [], 0
        batch.append(page)
        size += page_size
    if batch:
        yield batch


def annotate(pages: list[Page], allowed: set[int] | None = None) -> tuple[str, dict[int, Candidate]]:
    annotated = []
    candidates: dict[int, Candidate] = {}
    identifier = 0
    for page in pages:
        annotated.append(f"=== PDF page {page.number} ===")
        lines = page.text.splitlines()
        if page.graphics_text:
            lines.extend(["=== Graphics OCR ===", *page.graphics_text.splitlines()])
        for line in lines:
            parts = []
            cursor = 0
            for match in NUMBER.finditer(line):
                identifier += 1
                if allowed is not None and identifier not in allowed:
                    continue
                end = match.end()
                candidates[identifier] = make_candidate(page.number, line, match)
                parts.extend((line[cursor:end], f"[v{identifier}]"))
                cursor = end
            parts.append(line[cursor:])
            annotated.append("".join(parts))
    return "\n".join(annotated), candidates


def make_candidate(page: int, line: str, match: re.Match) -> Candidate:
    start, end = match.span()
    raw = match.group()
    numerals = raw.strip().lstrip("≈~").removesuffix("%").strip()
    value = Decimal(numerals.replace(",", "").replace("−", "-"))
    quote = line[max(start - 90, 0):end + 90].strip()
    return Candidate(page, raw, value, quote)


def resolve(groups: list[dict], candidates: dict[int, Candidate]) -> tuple[list[Observation], int]:
    observations = []
    used: set[int] = set()
    rejected = 0
    for group in groups:
        metric = group["metric"].strip()
        citation = group["citation"].strip()
        source = group["source"].strip()
        cutoff = re.search(r"@\s*(\d+)\s*$", metric)
        for identifier in group["ids"]:
            if not metric or identifier in used or identifier not in candidates:
                rejected += 1
                continue
            candidate = candidates[identifier]
            unit = group["unit"].strip() or ("%" if candidate.raw.endswith("%") else "")
            reference = citation if citation in candidate.quote else ""
            observations.append(Observation(
                candidate, metric, unit, int(cutoff[1]) if cutoff else None,
                "cited" if reference else "primary", reference, source,
            ))
            used.add(identifier)
    return observations, rejected
