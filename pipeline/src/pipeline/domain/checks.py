from collections import Counter
from dataclasses import dataclass
from typing import Any

from pipeline.domain.numbers import format_number, parse_number
from pipeline.domain.profile import PUBLICATION_ID, CheckSpec, Profile
from pipeline.domain.units import UnitBook

ISSUE_PREFIX = "I"
EXTRACTED = "extracted"


@dataclass(frozen=True)
class Value:
    row: dict[str, str]
    metric: str
    number: float
    text: str
    unit: str
    identity: tuple[str, ...]

    @property
    def publication_id(self) -> str:
        return self.row[PUBLICATION_ID]


@dataclass(frozen=True)
class Issue:
    publication_id: str
    records: tuple[dict[str, str], ...]
    check_name: str
    description: str


class Checker:
    def __init__(self, profile: Profile):
        self._profile = profile
        self._values = profile.values
        self._units = UnitBook(profile)
        self._identity = tuple(
            profile.values.column(name) for name in profile.values.duplicate_key if name not in ("metric", "value")
        )
        record_tables = {table.name for table in profile.record_tables}
        self._references = tuple(
            column for column, target in profile.values_table.references.items() if target in record_tables
        )
        self._rules = {
            "range": self._range,
            "contradiction": self._contradiction,
            "product": self._product,
            "agreement": self._agreement,
            "order": self._order,
        }

    def check(self, tables: dict[str, list[dict[str, str]]]) -> tuple[list[dict[str, str]], Counter]:
        values = self._eligible(tables.get(self._values.table, []))
        issues, report = [], Counter()
        for spec in self._profile.checks:
            rule = self._rules.get(spec.rule)
            if rule is None:
                report[f"правило не реализовано: {spec.name}"] += 1
                continue
            found = rule(spec, values, tables)
            report[spec.name] += len(found)
            issues += found
        return self._rows(issues), report

    def _eligible(self, rows: list[dict[str, str]]) -> list[Value]:
        column = self._values.column
        eligible = []
        for row in rows:
            number = parse_number(row.get(column("value")))
            if number is None or row.get(column("status"), EXTRACTED) != EXTRACTED or row.get(column("cited")):
                continue
            eligible.append(
                Value(
                    row,
                    row[column("metric")],
                    number,
                    row[column("value")],
                    row.get(column("unit"), ""),
                    tuple(row.get(name, "") for name in self._identity),
                )
            )
        return eligible

    def _range(self, spec: CheckSpec, values: list[Value], tables: dict) -> list[Issue]:
        issues = []
        for value in values:
            bounds = self._profile.ranges.get(value.metric)
            if self._excluded(spec, value, tables):
                continue
            if bounds and not bounds[0] <= value.number <= bounds[1]:
                description = (
                    f"{value.metric} {value.text} {value.unit} вне разумного диапазона "
                    f"{format_number(bounds[0])}–{format_number(bounds[1])} {value.unit}".replace("  ", " ")
                )
                issues.append(Issue(value.publication_id, (value.row,), spec.name, description))
        return issues

    def _contradiction(self, spec: CheckSpec, values: list[Value], tables: dict) -> list[Issue]:
        groups: dict[tuple, list[Value]] = {}
        evaluated = self._values.metrics
        for value in values:
            if self._linked(value) and (not evaluated or value.metric in evaluated):
                groups.setdefault((value.publication_id, value.metric, value.unit, value.identity), []).append(value)
        issues = []
        for group in groups.values():
            distinct = unique_by_number(group)
            sources = {value.row.get(self._values.column("table")) or "" for value in distinct}
            if len(distinct) < 2 or (len(sources) == 1 and "" not in sources):
                continue
            low, high = min(distinct, key=lambda value: value.number), max(distinct, key=lambda value: value.number)
            if high.number - low.number <= rounding(low.text, high.text):
                continue
            places = "; ".join(f"{value.text} (с. {value.row.get(self._values.column('page'), '')})" for value in distinct)
            description = f"{low.metric} одной записи в разных местах статьи: {places} {low.unit}".rstrip()
            issues.append(Issue(low.publication_id, tuple(value.row for value in distinct), spec.name, description))
        return issues

    def _product(self, spec: CheckSpec, values: list[Value], tables: dict) -> list[Issue]:
        target, factors = spec.params["target"], spec.params["factors"]
        tolerance = float(spec.params["tolerance"])
        issues = []
        for group in self._by_identity(values).values():
            chosen = {metric: self._primary(group, metric) for metric in (target, *factors)}
            if any(value is None for value in chosen.values()):
                continue
            divisor = self._divisor(spec.params.get("divisor"), chosen[target].row, tables)
            if not divisor or not chosen[target].number:
                continue
            product = float(spec.params.get("scale", 1))
            for factor in factors:
                product *= chosen[factor].number
            expected = product / divisor
            difference = abs(chosen[target].number - expected) / chosen[target].number
            if difference >= tolerance:
                formula = "·".join(factors) + ("/Pin" if spec.params.get("divisor") else "")
                description = (
                    f"{target} {chosen[target].text} {chosen[target].unit} не согласуется с {formula} = "
                    f"{expected:.2f} {chosen[target].unit} (расхождение {difference:.1%})"
                )
                records = (chosen[target].row, *(chosen[factor].row for factor in factors))
                issues.append(Issue(chosen[target].publication_id, records, spec.name, description))
        return issues

    def _divisor(self, spec: dict[str, Any] | None, row: dict[str, str], tables: dict) -> float | None:
        if not spec:
            return 1.0
        table = self._profile.table(spec["table"])
        record_id = row.get(spec["via"], "")
        record = next(
            (
                item
                for item in tables.get(table.name, [])
                if item[PUBLICATION_ID] == row[PUBLICATION_ID] and item[table.id] == record_id
            ),
            None,
        )
        if record and parse_number(record.get(spec["value"])) is not None:
            converted = self._units.convert(parse_number(record[spec["value"]]), record.get(spec["unit"]), spec["target_unit"])
            if converted.value:
                return converted.value
        return float(spec["default"]) if "default" in spec else None

    def _agreement(self, spec: CheckSpec, values: list[Value], tables: dict) -> list[Issue]:
        first, second = spec.params["metrics"]
        tolerance = float(spec.params["tolerance"])
        by = spec.params["by"]
        groups: dict[tuple, list[Value]] = {}
        for value in values:
            key = tuple(value.row.get(column, "") for column in by)
            if all(key):
                groups.setdefault((value.publication_id, key), []).append(value)
        issues = []
        for group in groups.values():
            for other in unique_by_number([value for value in group if value.metric == second]):
                candidates = unique_by_number([value for value in group if value.metric == first])
                if not candidates:
                    continue
                closest = min(candidates, key=lambda value: abs(value.number - other.number))
                difference = abs(closest.number - other.number) / closest.number if closest.number else 0
                if difference > tolerance:
                    description = (
                        f"{first} {closest.text} и {second} {other.text} {closest.unit} одного прибора "
                        f"расходятся на {difference:.1%} (допуск {tolerance:.0%})"
                    )
                    issues.append(Issue(closest.publication_id, (closest.row, other.row), spec.name, description))
        return issues

    def _order(self, spec: CheckSpec, values: list[Value], tables: dict) -> list[Issue]:
        lower, upper = spec.params["lower"], spec.params["upper"]
        aggregation = self._values.column("aggregation")
        identity = tuple(name for name in self._identity if name != aggregation)
        groups: dict[tuple, list[Value]] = {}
        for value in values:
            if value.metric in spec.params["metrics"]:
                key = tuple(value.row.get(name, "") for name in identity)
                if any(key):
                    groups.setdefault((value.publication_id, value.metric, key), []).append(value)
        issues = []
        for group in groups.values():
            means = unique_by_number([value for value in group if value.row.get(aggregation) == lower])
            bests = unique_by_number([value for value in group if value.row.get(aggregation) == upper])
            for mean in means:
                for best in bests:
                    if mean.number > best.number:
                        description = (
                            f"{mean.metric}: {lower} {mean.text} {mean.unit} больше, чем {upper} {best.text} {best.unit}"
                        )
                        issues.append(Issue(mean.publication_id, (mean.row, best.row), spec.name, description))
        return issues

    def _excluded(self, spec: CheckSpec, value: Value, tables: dict) -> bool:
        unless = spec.params.get("unless")
        if not unless:
            return False
        table = self._profile.table(unless["table"])
        record_id = value.row.get(unless["via"], "")
        return any(
            record[PUBLICATION_ID] == value.publication_id
            and record[table.id] == record_id
            and record.get(unless["column"]) in unless["values"]
            for record in tables.get(table.name, [])
        )

    def _by_identity(self, values: list[Value]) -> dict[tuple, list[Value]]:
        groups: dict[tuple, list[Value]] = {}
        for value in values:
            if self._linked(value):
                groups.setdefault((value.publication_id, value.identity), []).append(value)
        return groups

    def _linked(self, value: Value) -> bool:
        return any(value.row.get(column) for column in self._references)

    def _primary(self, group: list[Value], metric: str) -> Value | None:
        candidates = [value for value in group if value.metric == metric]
        duplicate = self._values.column("duplicate_of")
        return next((value for value in candidates if not value.row.get(duplicate)), candidates[0] if candidates else None)

    def _rows(self, issues: list[Issue]) -> list[dict[str, str]]:
        column = self._values.column
        rows, numbers = [], Counter()
        for issue in issues:
            numbers[issue.publication_id] += 1
            source = issue.records[0]
            rows.append(
                {
                    "publication_id": issue.publication_id,
                    "issue_id": f"{ISSUE_PREFIX}{numbers[issue.publication_id]}",
                    "record_ids": ";".join(dict.fromkeys(record[column("id")] for record in issue.records)),
                    "check_name": issue.check_name,
                    "description": issue.description,
                    "source_page": source.get(column("page"), ""),
                    "source_fragment": source.get(column("fragment"), ""),
                }
            )
        return rows


def unique_by_number(values: list[Value]) -> list[Value]:
    seen: dict[float, Value] = {}
    for value in values:
        seen.setdefault(round(value.number, 9), value)
    return list(seen.values())


def rounding(first: str, second: str) -> float:
    return 0.5 * 10 ** -min(decimals(first), decimals(second)) + 1e-9


def decimals(text: str) -> int:
    return len(text.split(".", 1)[1]) if "." in text else 0
