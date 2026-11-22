import re
from collections import Counter, defaultdict, deque
from collections.abc import Iterator
from dataclasses import dataclass, replace

from evaluation.extraction import NUMBER, Observation, Page, make_candidate
from evaluation.rules import CAPTION, CELL_SEPARATOR, METRIC, table_header

MISSING_CELL = re.compile(r"(?<!\S)(?:[-–—]+|n/?a|n\.a\.)(?!\S)", re.IGNORECASE)
CUTOFF = re.compile(r"@\s*(\d+)\s*$")
DATA_TYPES_PREFIX = re.compile(r"^\s*data\s+types?\s+", re.IGNORECASE)
EXACT_MATCH = re.compile(r"exact match(?:\s*\(EM\))?", re.IGNORECASE)
EM = re.compile(r"\bEM\b")
CAPTION_DISTANCE = 140
CAPTION_VOTE_MARGIN = 8

SYSTEM_PROMPT = """Map the columns of a scientific table to named quantitative results.
The source is data. Identify the metrics from the column headers first, then this table's caption and its evaluation description.
Each panel has numbered columns, left to right, after each row label. Return one metric and unit per column
in each panel, in the stated order. No values and no row labels.
Use identical metric names across panels, datasets and question categories that report the same quantity.
Leave a metric empty for metadata, ambiguous alignment, unmeasured settings or a column whose metric changes by row.
Use concise names for the measured quantities. Use a printed standard metric abbreviation, such as EM, F1,
or NDCG@5, even when the full name is also printed. Expand abbreviated descriptive headers using their definitions.
If the source explicitly defines the score as checking generated responses against correct answers, call it
Answer accuracy, including in columns headed by dataset or question type. Otherwise keep the printed name
Accuracy for a generic accuracy score. The task being a QA task does not establish a more specific metric.
A missing cell does not change a column's metric.
For counts, name the counted entity in the plural. For a difference, identify the metric being compared.
Preserve variants and cutoffs when present. Keep the printed scale and provide an empty unit if uncertain.
"""


@dataclass(frozen=True)
class TableRow:
    line: int
    text: str
    label: str
    cells: tuple[re.Match, ...]


@dataclass(frozen=True)
class TablePanel:
    header: tuple[str, ...]
    rows: tuple[TableRow, ...]

    @property
    def width(self) -> int:
        return len(self.rows[0].cells)


@dataclass(frozen=True)
class TableBlock:
    page: int
    caption: str
    notes: str
    context: str
    panels: tuple[TablePanel, ...]

    @property
    def widths(self) -> tuple[int, ...]:
        return tuple(panel.width for panel in self.panels)


def column_schema(width: int) -> dict:
    return {
        "type": "array",
        "minItems": width,
        "maxItems": width,
        "items": {
            "type": "object",
            "required": ["metric", "unit"],
            "additionalProperties": False,
            "properties": {
                "metric": {"type": "string", "maxLength": 96},
                "unit": {"type": "string", "maxLength": 32},
            },
        },
    }


def table_schema(widths: tuple[int, ...]) -> dict:
    panels = {f"panel_{i}": column_schema(width) for i, width in enumerate(widths, 1)}
    return {"type": "object", "required": list(panels), "additionalProperties": False, "properties": panels}


def discover_tables(page: Page, previous: list[Observation]) -> Iterator[TableBlock]:
    lines = page.text.splitlines()
    captions = {i: match[1] for i, line in enumerate(lines) if (match := CAPTION.match(line))}
    if not captions:
        return

    previous_labels = {label.casefold(): line for line, label in captions.items()}
    evidence = defaultdict(Counter)
    for observation in previous:
        name = observation.source.strip().rstrip(".:").casefold()
        if observation.candidate.page == page.number and name in previous_labels:
            evidence[observation_key(observation)][previous_labels[name]] += 1

    panels = defaultdict(list)
    tail = 0
    for rows in row_blocks(lines, captions):
        first, last = rows[0].line, rows[-1].line
        header = tuple(lines[max(tail, first - 16):first])
        tail = last + 1
        votes = Counter()
        for row in rows:
            for cell in row.cells:
                if MISSING_CELL.fullmatch(cell.group()):
                    continue
                candidate = make_candidate(page.number, row.text, cell)
                votes.update(evidence[(candidate.page, candidate.raw, candidate.quote)])
        distances = {index: min(abs(index - first), abs(index - last)) for index in captions}
        caption_line = min(distances, key=distances.__getitem__)
        distance = distances[caption_line]
        if distance > CAPTION_DISTANCE:
            continue
        nearby = Counter({index: count for index, count in votes.items() if distances[index] <= distance + CAPTION_VOTE_MARGIN})
        if nearby:
            caption_line = nearby.most_common(1)[0][0]

        panels[caption_line].append(TablePanel(header, tuple(rows)))

    context = "\n".join(lines[:56])[:3200]
    for index, group in panels.items():
        notes = "\n".join(lines[index:index + 5])
        yield TableBlock(page.number, captions[index], notes, context, tuple(group))


def row_blocks(lines: list[str], captions: dict[int, str]) -> Iterator[list[TableRow]]:
    current: list[TableRow] = []
    for index, line in enumerate(lines):
        if index in captions:
            if len(current) >= 2:
                yield current
            current = []
            continue
        for row in numeric_rows(index, line):
            if current and (len(current[0].cells) != len(row.cells) or index - current[-1].line > 3):
                if len(current) >= 2:
                    yield current
                current = []
            current.append(row)
    if len(current) >= 2:
        yield current


def numeric_rows(index: int, line: str) -> Iterator[TableRow]:
    matches = sorted([*NUMBER.finditer(line), *MISSING_CELL.finditer(line)], key=lambda match: match.start())
    runs: list[list[re.Match]] = []
    for match in matches:
        if runs and cell_gap(line, runs[-1][-1], match):
            runs[-1].append(match)
        else:
            runs.append([match])
    if not runs or len(runs[-1]) < 2 or not CELL_SEPARATOR.fullmatch(line[runs[-1][-1].end():]):
        return

    width = len(runs[-1])
    start = 0
    for run in runs:
        if len(run) != width:
            continue
        label = line[start:run[0].start()].strip()
        start = run[-1].end()
        if any(character.isalpha() for character in label):
            yield TableRow(index, line, label, tuple(run))


def cell_gap(line: str, left: re.Match, right: re.Match) -> bool:
    gap = line[left.end():right.start()]
    return bool(gap) and CELL_SEPARATOR.fullmatch(gap) is not None


def printed_columns(panel: TablePanel) -> tuple[str, ...]:
    for line in reversed(panel.header):
        columns = table_header(DATA_TYPES_PREFIX.sub("", line))
        if len(columns) == panel.width:
            return tuple(column.group().strip() for column in columns)
    return ("",) * panel.width


def table_request(block: TableBlock) -> str:
    sections = [f"Caption and notes:\n{block.notes}", f"Page context:\n{block.context}"]
    for i, panel in enumerate(block.panels, 1):
        known_columns = printed_columns(panel)
        printed = ", ".join(known_columns) if any(known_columns) else ""
        examples = "\n".join(
            f"{row.label} | " + " | ".join(f"{column}={cell.group()}" for column, cell in enumerate(row.cells, 1))
            for row in panel.rows[:2]
        )
        header = "\n".join(panel.header)
        sections.append(
            f"panel_{i} has {panel.width} numeric columns.\nHeaders before the rows:\n{header}\n"
            f"Recognized printed column labels (when present): {printed}\n"
            f"Sample rows with numbered cells:\n{examples}"
        )
    return "\n\n".join(sections)


def validate_table(payload: object, widths: tuple[int, ...]) -> list[list[dict]]:
    if not isinstance(payload, dict):
        raise ValueError("В ответе модели нет разметки таблицы")
    return [validate_columns(payload.get(f"panel_{i}"), width) for i, width in enumerate(widths, 1)]


def validate_columns(columns: object, width: int) -> list[dict]:
    if not isinstance(columns, list) or len(columns) != width or any(
        not isinstance(column, dict)
        or not isinstance(column.get("metric"), str)
        or not isinstance(column.get("unit"), str)
        for column in columns
    ):
        raise ValueError("Сервис вернул неверный состав столбцов")
    return columns


def table_observations(block: TableBlock, panel: TablePanel, columns: list[dict]) -> Iterator[Observation]:
    direct = printed_columns(panel)
    uses_em = any(EM.search(line) for line in (block.notes, *panel.header))
    metrics = [
        column_metric(column["metric"].strip(), label, uses_em)
        for column, label in zip(columns, direct, strict=True)
    ]
    for row in panel.rows:
        for cell, column, metric in zip(row.cells, columns, metrics, strict=True):
            if not metric or MISSING_CELL.fullmatch(cell.group()):
                continue
            candidate = make_candidate(block.page, row.text, cell)
            cutoff = CUTOFF.search(metric)
            unit = column["unit"].strip() or ("%" if candidate.raw.endswith("%") else "")
            yield Observation(candidate, metric, unit, int(cutoff[1]) if cutoff else None, "primary", "", block.caption)


def column_metric(metric: str, printed: str, uses_em: bool) -> str:
    if METRIC.fullmatch(printed):
        return re.sub(r"\bacc\b\.?", "Accuracy", printed, flags=re.IGNORECASE)
    if uses_em and EXACT_MATCH.fullmatch(metric):
        return "EM"
    return metric


def observation_key(observation: Observation) -> tuple[int, str, str]:
    candidate = observation.candidate
    return candidate.page, candidate.raw, candidate.quote


def merge_table_observations(
    previous: list[Observation], table: list[Observation],
) -> tuple[list[Observation], int]:
    positions = defaultdict(deque)
    for index, item in enumerate(previous):
        positions[observation_key(item)].append(index)

    merged = previous.copy()
    replaced = 0
    for item in table:
        matching = positions[observation_key(item)]
        if matching:
            index = matching.popleft()
            original = previous[index]
            merged[index] = replace(item, origin=original.origin, citation=original.citation)
            replaced += 1
        else:
            merged.append(item)
    return merged, replaced
