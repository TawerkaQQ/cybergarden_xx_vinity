import re
from collections import Counter
from dataclasses import dataclass, field
from typing import Any

from pipeline.domain.extraction_shape import IN_SCOPE, ExtractionShape, RecordShape
from pipeline.domain.numbers import format_number, parse_number
from pipeline.domain.profile import PUBLICATION_ID, Profile, Table
from pipeline.domain.quotes import QuoteLocator
from pipeline.domain.units import Converted, UnitBook

LIST_SEPARATOR = ";"
NOTE = "note"
TABLE_QUOTE_PREFIXES = ("table", "supplementary")
ROW_LABEL_MARK = "строка"
UNIT_SUFFIX = "_unit"
NULL_WORDS = {"null", "none"}
QUANTITY = re.compile(r"(-?\d+(?:[.,]\d+)?)\s*([^\d\s].*)")


@dataclass
class AssembledSet:
    tables: dict[str, list[dict[str, str]]]
    report: Counter = field(default_factory=Counter)


class Assembler:
    def __init__(self, shape: ExtractionShape):
        self._shape = shape
        self._profile: Profile = shape.profile
        self._units = UnitBook(shape.profile)
        self._prefixes = id_prefixes(self._profile)

    def assemble(
        self, publication_ids: list[str], extracts: dict[str, dict[str, Any]], pages: dict[str, dict[int, str]]
    ) -> AssembledSet:
        assembled = AssembledSet({table.name: [] for table in self._profile.tables})
        for publication_id in publication_ids:
            extract = extracts.get(publication_id)
            assembled.tables[self._profile.publications.name].append(self._publication(publication_id, extract))
            if extract is None:
                assembled.report["нет extract.json"] += 1
                continue
            if not extract["publication"].get(IN_SCOPE):
                continue
            ids = self._record_ids(extract)
            records = {shape.table.name: self._records(publication_id, shape, extract, ids) for shape in self._shape.records}
            for table, rows in records.items():
                assembled.tables[table] += rows
            locator = QuoteLocator(pages.get(publication_id, {}))
            values = self._values(publication_id, extract, ids, locator, assembled.report)
            self._infer_references(values, records, assembled.report)
            assembled.tables[self._profile.values.table] += values
        return assembled

    def _infer_references(
        self, values: list[dict[str, str]], records: dict[str, list[dict[str, str]]], report: Counter
    ) -> None:
        cited_column = self._profile.values.column("cited")
        for row in values:
            if row.get(cited_column):
                continue
            for column, target in self._shape.mention.refs.items():
                if row[column]:
                    continue
                candidates = self._reference_candidates(row, target, records)
                if len(candidates) == 1:
                    row[column] = candidates.pop()
                    report[f"ссылка выведена: {column}"] += 1

    def _reference_candidates(self, row: dict[str, str], target: str, records: dict[str, list[dict[str, str]]]) -> set[str]:
        target_id = self._profile.table(target).id
        if len(records[target]) == 1:
            return {records[target][0][target_id]}
        candidates: set[str] = set()
        for column, linked in self._shape.mention.refs.items():
            if not row[column] or linked == target:
                continue
            linked_table = self._profile.table(linked)
            linked_row = next((record for record in records[linked] if record[linked_table.id] == row[column]), None)
            for reference, referenced in linked_table.references.items():
                if referenced == target and linked_row and linked_row.get(reference):
                    candidates |= set(linked_row[reference].split(LIST_SEPARATOR))
            for target_row in records[target]:
                for reference, referenced in self._profile.table(target).references.items():
                    if referenced == linked and row[column] in target_row.get(reference, "").split(LIST_SEPARATOR):
                        candidates.add(target_row[target_id])
        return candidates

    def _publication(self, publication_id: str, extract: dict[str, Any] | None) -> dict[str, str]:
        table = self._profile.publications
        row = empty_row(table, publication_id)
        for column, value in (extract or {}).get("publication", {}).items():
            if column in row:
                row[column] = cell(value)
        return row

    def _record_ids(self, extract: dict[str, Any]) -> dict[str, dict[str, str]]:
        return {
            shape.table.name: {
                record["key"]: f"{self._prefixes[shape.table.name]}{number}"
                for number, record in enumerate(extract["records"].get(shape.table.name, []), 1)
            }
            for shape in self._shape.records
        }

    def _records(
        self, publication_id: str, shape: RecordShape, extract: dict[str, Any], ids: dict[str, dict[str, str]]
    ) -> list[dict[str, str]]:
        table = shape.table
        rows = []
        for record in extract["records"].get(table.name, []):
            row = empty_row(table, publication_id)
            row[table.id] = ids[table.name][record["key"]]
            for column in shape.fields:
                row[column] = cell(record["fields"].get(column))
            for column, target in shape.refs.items():
                row[column] = reference(record["refs"].get(column), ids[target])
            self._normalize_units(table, row)
            self._canonical_unit_spelling(table, shape.fields, row)
            rows.append(row)
        return rows

    def _canonical_unit_spelling(self, table: Table, fields: tuple[str, ...], row: dict[str, str]) -> None:
        unit_columns = set(table.unit_columns)
        for column in fields:
            text = row.get(column, "")
            if not text:
                continue
            if column in unit_columns or column.endswith(UNIT_SUFFIX):
                row[column] = self._units.canonical(text)
                continue
            quantity = QUANTITY.fullmatch(text)
            if quantity and self._units.knows(quantity.group(2)):
                row[column] = f"{quantity.group(1)} {self._units.canonical(quantity.group(2))}"

    def _normalize_units(self, table: Table, row: dict[str, str]) -> None:
        for column, unit in table.units.items():
            raw = row.get(column, "")
            if not raw:
                continue
            converted = self._units.convert(parse_number(raw), row.get(unit.unit_column), unit.target)
            if converted.value is None or not converted.recognized:
                add_note(row, f"{column}: {raw} {row.get(unit.unit_column, '')}".strip())
            row[column] = format_number(converted.value) if converted.recognized else ""
            row[unit.unit_column] = converted.unit if converted.recognized and converted.value is not None else ""

    def _values(
        self,
        publication_id: str,
        extract: dict[str, Any],
        ids: dict[str, dict[str, str]],
        locator: QuoteLocator,
        report: Counter,
    ) -> list[dict[str, str]]:
        table = self._profile.values_table
        mentions = sorted(enumerate(extract["mentions"]), key=lambda item: (item[1]["page"] or 0, item[0]))
        rows = [self._value(publication_id, mention, ids, locator, report) for _, mention in mentions]
        width = max(3, len(str(len(rows))))
        for number, row in enumerate(rows, 1):
            row[table.id] = f"{self._prefixes[table.name]}{number:0{width}d}"
        self._link_duplicates(rows)
        return rows

    def _value(
        self,
        publication_id: str,
        mention: dict[str, Any],
        ids: dict[str, dict[str, str]],
        locator: QuoteLocator,
        report: Counter,
    ) -> dict[str, str]:
        values = self._profile.values
        shape = self._shape.mention
        row = empty_row(self._profile.values_table, publication_id)
        cited = bool(mention.get("cited"))
        for column, target in shape.refs.items():
            row[column] = "" if cited else reference(mention["refs"].get(column), ids[target])
        for column in shape.fields:
            row[column] = cell(mention["fields"].get(column))
        for key, column in shape.columns.items():
            row[column] = cell(mention.get(key))
        raw_unit = self._units.canonical(mention.get("raw_unit") or mention.get("unit"))
        row[values.column("raw_unit")] = raw_unit
        metric = mention["metric"]
        number = parse_number(mention.get("raw_value"))
        converted = self._units.convert(number, raw_unit, values.metrics.get(metric))
        inferred = self._unit_by_range(metric, number, raw_unit, converted)
        if inferred:
            raw_unit, converted = inferred
            row[values.column("raw_unit")] = raw_unit
            report["единица выведена по диапазону"] += 1
            add_note(row, f"по разумному диапазону {metric} единица — {raw_unit}, а не «{mention.get('raw_unit') or mention.get('unit') or 'не указана'}»")
        if mention.get("raw_value") and converted.value is None:
            report["значение не разобрано"] += 1
            add_note(row, f"значение «{mention['raw_value']} {raw_unit}» не переведено в целевую единицу".strip())
        elif not converted.recognized:
            report["единица не распознана"] += 1
            add_note(row, f"единица «{raw_unit}» не распознана, принята {converted.unit}")
        row[values.column("value")] = format_number(converted.value)
        row[values.column("unit")] = converted.unit if converted.value is not None else ""
        for mirror, source in values.mirrors.items():
            row[mirror] = row[values.column(source)]
        self._place_quote(row, mention, locator, report)
        return row

    def _unit_by_range(self, metric: str, number: float | None, raw_unit: str, converted: Converted) -> tuple[str, Converted] | None:
        target = self._profile.values.metrics.get(metric)
        bounds = self._profile.ranges.get(metric)
        if number is None or not target or not bounds:
            return None
        if converted.value is not None and bounds[0] <= converted.value <= bounds[1]:
            return None
        options = [(target, 1.0)] + [(conversion.source, conversion.factor) for conversion in self._profile.conversions if conversion.target == target]
        candidates = [
            (unit, Converted(number * factor, target, True))
            for unit, factor in options
            if unit != raw_unit and bounds[0] <= number * factor <= bounds[1]
        ]
        return candidates[0] if len(candidates) == 1 else None

    def _place_quote(self, row: dict[str, str], mention: dict[str, Any], locator: QuoteLocator, report: Counter) -> None:
        values = self._profile.values
        quote = (mention.get("quote") or "").strip()
        if self._is_table_label(quote, mention):
            row_label = quote.split(ROW_LABEL_MARK, 1)[1].strip() if ROW_LABEL_MARK in quote else ""
            page = locator.table_page(mention["table"], row_label, mention.get("page"), mention.get("raw_value"))
            row[values.column("page")] = str(page) if page else ""
            fragment = locator.row_fragment(page, row_label, mention.get("raw_value"))
            if fragment:
                row[values.column("fragment")] = fragment
            report["цитата: строка таблицы дословно" if fragment else "цитата: подпись строки таблицы"] += 1
            return
        located = locator.locate(quote, mention.get("page"), mention.get("raw_value"))
        if located is None:
            report["цитата: не найдена"] += 1
            return
        report[f"цитата: {located.how}"] += 1
        row[values.column("fragment")] = located.fragment
        row[values.column("page")] = str(located.page)

    def _is_table_label(self, quote: str, mention: dict[str, Any]) -> bool:
        return (
            self._profile.table_fragment == "row_label"
            and bool(mention.get("table"))
            and quote.lower().startswith(TABLE_QUOTE_PREFIXES)
        )

    def _link_duplicates(self, rows: list[dict[str, str]]) -> None:
        values = self._profile.values
        key_columns = [values.column(name) for name in values.duplicate_key]
        value_column = values.column("value")
        id_column = values.column("id")
        groups: dict[tuple[str, ...], list[dict[str, str]]] = {}
        for row in rows:
            if row[value_column]:
                groups.setdefault(tuple(row.get(column, "") for column in key_columns), []).append(row)
        for group in groups.values():
            primary = next((row for row in group if self._from_table_or_figure(row)), group[0])
            for row in group:
                if row is not primary:
                    row[values.column("duplicate_of")] = primary[id_column]

    def _from_table_or_figure(self, row: dict[str, str]) -> bool:
        values = self._profile.values
        return bool(row.get(values.column("table")) or row.get(values.column("figure")))


def id_prefixes(profile: Profile) -> dict[str, str]:
    prefixes: dict[str, str] = {}
    for table in profile.tables:
        if table.name == profile.publications.name:
            continue
        length = 1
        while table.name[:length].upper() in prefixes.values():
            length += 1
        prefixes[table.name] = table.name[:length].upper()
    return prefixes


def empty_row(table: Table, publication_id: str) -> dict[str, str]:
    row = {column: "" for column in table.columns}
    row[PUBLICATION_ID] = publication_id
    return row


def cell(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, list):
        return LIST_SEPARATOR.join(cell(item) for item in value)
    text = " ".join(str(value).split())
    return "" if text.casefold() in NULL_WORDS else text


def reference(value: Any, ids: dict[str, str]) -> str:
    if isinstance(value, list):
        return LIST_SEPARATOR.join(ids[key] for key in value if key in ids)
    return ids.get(value, "") if value else ""


def add_note(row: dict[str, str], text: str) -> None:
    if NOTE in row:
        row[NOTE] = f"{row[NOTE]}; {text}" if row[NOTE] else text


def highlight_text(fragment: str) -> str:
    if fragment.lower().startswith(TABLE_QUOTE_PREFIXES) and ROW_LABEL_MARK in fragment:
        return fragment.split(ROW_LABEL_MARK, 1)[1].strip()
    return fragment
