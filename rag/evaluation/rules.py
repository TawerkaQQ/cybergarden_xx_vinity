import re
from collections.abc import Iterator
from dataclasses import dataclass

from evaluation.extraction import NUMBER, Observation, Page, make_candidate

VERSION = "rules-v1"
METRIC = re.compile(
    r"(?<!\w)(?:(?:answer|knowledge|retrieval|QA|generation) )?"
    r"(?:accuracy|acc\.?|exact match|EM|F1|precision|recall|NDCG|MRR|MAP|"
    r"ROUGE(?:-[0-9L])?|BLEU(?:-[0-9])?|AUROC|ROC-AUC|AUC|hit rate|latency)"
    r"(?:\s*@\s*\d+)?(?!\w)",
    re.IGNORECASE,
)
TABLE_COLUMN = re.compile(rf"{METRIC.pattern}|(?<!\w)(?:step|time)(?!\w)", re.IGNORECASE)
CAPTION = re.compile(r"^\s*(Table\s+(?:[A-Z]?\d+[a-z]?|[IVXLCDM]+))(?:\s*[:.]|\s*$)", re.IGNORECASE)
HEADER_PREFIX = re.compile(r"(?:\s|(?:types?|methods?|datasets?|models?|systems?|scores?|metrics?)\b)*", re.IGNORECASE)
COLUMN_SEPARATOR = re.compile(r"[\s|/↑↓]*(?:\(%\)[\s|/]*)?")
CELL_SEPARATOR = re.compile(r"[\s†‡*∗↑↓]*")
VALUE_LINK = re.compile(r"\s*(?:=|:|of\b|was\b|is\b|at\b|reached\b|achieved\b)\s*", re.IGNORECASE)
NUMBER_TO_METRIC = re.compile(r"\s+")
EXCLUDED_LINE = re.compile(r"^\s*(?:\[\d+\]|(?:Fig\.|Figure|Table)\s+\d+\s*[:.])", re.IGNORECASE)


@dataclass(frozen=True)
class TableRow:
    line_number: int
    metrics: list[re.Match]
    numbers: list[re.Match]


def observations(page: Page) -> list[Observation]:
    lines = page.text.splitlines()
    captions = [(index, match[1]) for index, line in enumerate(lines) if (match := CAPTION.match(line))]
    selected = set()
    extracted = []
    for row in table_rows(lines):
        index = row.line_number
        if not captions:
            continue
        caption_index, label = min(captions, key=lambda caption: abs(caption[0] - index))
        if abs(caption_index - index) > 36:
            continue
        for metric, number in zip(row.metrics, row.numbers, strict=True):
            extracted.append(to_observation(page.number, lines[index], metric, number, label))
            selected.add((index, number.start()))

    for index, line in enumerate(lines):
        if EXCLUDED_LINE.match(line):
            continue
        for metric, number in prose_pairs(line):
            key = index, number.start()
            if key not in selected:
                extracted.append(to_observation(page.number, line, metric, number, ""))
                selected.add(key)
    return extracted


def table_rows(lines: list[str]) -> Iterator[TableRow]:
    metrics: list[re.Match] = []
    gap = 0
    for index, line in enumerate(lines):
        if header := table_header(line):
            metrics, gap = header, 0
            continue
        if not metrics:
            continue
        numbers = trailing_numbers(line)
        if len(numbers) == len(metrics):
            yield TableRow(index, metrics, numbers)
            gap = 0
        else:
            gap += 1
            if gap >= 3:
                metrics = []


def table_header(line: str) -> list[re.Match]:
    columns = list(TABLE_COLUMN.finditer(line))
    if len(columns) < 2 or not HEADER_PREFIX.fullmatch(line[:columns[0].start()]):
        return []
    ends = [column.end() for column in columns]
    next_starts = [column.start() for column in columns[1:]] + [len(line)]
    if all(COLUMN_SEPARATOR.fullmatch(line[end:start]) for end, start in zip(ends, next_starts, strict=True)):
        return columns
    return []


def trailing_numbers(line: str) -> list[re.Match]:
    numbers = []
    end = len(line)
    for match in reversed(list(NUMBER.finditer(line))):
        if not CELL_SEPARATOR.fullmatch(line[match.end():end]):
            break
        numbers.append(match)
        end = match.start()
    if not any(letter.isalpha() for letter in line[:end]):
        return []
    return numbers[::-1]


def prose_pairs(line: str) -> Iterator[tuple[re.Match, re.Match]]:
    metrics = list(METRIC.finditer(line))
    for number in NUMBER.finditer(line):
        for metric in metrics:
            if VALUE_LINK.fullmatch(line[metric.end():number.start()]) and metric.end() < number.start():
                yield metric, number
                break
            if (
                number.end() < metric.start()
                and NUMBER_TO_METRIC.fullmatch(line[number.end():metric.start()])
                and (number.group().endswith("%") or "." in number.group())
            ):
                yield metric, number
                break


def to_observation(page: int, line: str, metric: re.Match, number: re.Match, source: str) -> Observation:
    name, marker, k = metric.group().strip().partition("@")
    candidate = make_candidate(page, line, number)
    return Observation(candidate, name.strip(), "%" if candidate.raw.endswith("%") else "",
                       int(k) if marker else None, "primary", "", source)
