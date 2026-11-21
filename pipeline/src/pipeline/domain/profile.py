from dataclasses import dataclass
from typing import Any

PUBLICATIONS = "publications"
PUBLICATION_ID = "publication_id"
ISSUE_COLUMNS = ("publication_id", "issue_id", "record_ids", "check_name", "description", "source_page", "source_fragment")


@dataclass(frozen=True)
class UnitTarget:
    unit_column: str
    target: str


@dataclass(frozen=True)
class Table:
    name: str
    file: str
    id: str
    columns: tuple[str, ...]
    references: dict[str, str]
    lists: tuple[str, ...]
    vocabularies: dict[str, tuple[str, ...]]
    units: dict[str, UnitTarget]
    descriptions: dict[str, str]

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "Table":
        return cls(
            name=payload["name"],
            file=payload.get("file", f"{payload['name']}.csv"),
            id=payload["id"],
            columns=tuple(payload["columns"]),
            references=dict(payload.get("references", {})),
            lists=tuple(payload.get("lists", [])),
            vocabularies={column: tuple(values) for column, values in payload.get("vocabularies", {}).items()},
            units={
                column: UnitTarget(unit["unit_column"], unit["target"])
                for column, unit in payload.get("units", {}).items()
            },
            descriptions=dict(payload.get("descriptions", {})),
        )

    @property
    def unit_columns(self) -> tuple[str, ...]:
        return tuple(unit.unit_column for unit in self.units.values())


@dataclass(frozen=True)
class ValuesSpec:
    table: str
    roles: dict[str, str]
    mirrors: dict[str, str]
    metrics: dict[str, str]
    duplicate_key: tuple[str, ...]
    match: dict[str, tuple[str, ...]]

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "ValuesSpec":
        return cls(
            table=payload["table"],
            roles=dict(payload["columns"]),
            mirrors=dict(payload.get("mirrors", {})),
            metrics=dict(payload.get("metrics", {})),
            duplicate_key=tuple(payload.get("duplicate_key", [])),
            match={mode: tuple(fields) for mode, fields in payload.get("match", {}).items()},
        )

    def column(self, name: str) -> str:
        return self.roles.get(name, name)


@dataclass(frozen=True)
class CheckSpec:
    rule: str
    name: str
    params: dict[str, Any]

    @classmethod
    def from_json(cls, payload: str | dict[str, Any]) -> "CheckSpec":
        if isinstance(payload, str):
            return cls(payload, payload, {})
        params = {key: value for key, value in payload.items() if key not in ("rule", "name")}
        return cls(payload["rule"], payload.get("name", payload["rule"]), params)


@dataclass(frozen=True)
class Conversion:
    source: str
    target: str
    factor: float


@dataclass(frozen=True)
class Profile:
    name: str
    title: str
    scope: str
    tables: tuple[Table, ...]
    values: ValuesSpec
    table_fragment: str
    unit_aliases: dict[str, str]
    conversions: tuple[Conversion, ...]
    ranges: dict[str, tuple[float, float]]
    checks: tuple[CheckSpec, ...]
    rules: str

    @classmethod
    def from_json(cls, payload: dict[str, Any], rules: str) -> "Profile":
        units = payload.get("units", {})
        return cls(
            name=payload["name"],
            title=payload["title"],
            scope=payload["scope"],
            tables=tuple(Table.from_json(table) for table in payload["tables"]),
            values=ValuesSpec.from_json(payload["values"]),
            table_fragment=payload.get("sources", {}).get("table_fragment", "literal"),
            unit_aliases=dict(units.get("aliases", {})),
            conversions=tuple(
                Conversion(item["from"], item["to"], float(item["factor"])) for item in units.get("conversions", [])
            ),
            ranges={metric: (float(low), float(high)) for metric, (low, high) in payload.get("ranges", {}).items()},
            checks=tuple(CheckSpec.from_json(check) for check in payload.get("checks", [])),
            rules=rules,
        )

    def table(self, name: str) -> Table:
        return next(table for table in self.tables if table.name == name)

    @property
    def publications(self) -> Table:
        return self.table(PUBLICATIONS)

    @property
    def values_table(self) -> Table:
        return self.table(self.values.table)

    @property
    def record_tables(self) -> tuple[Table, ...]:
        return tuple(table for table in self.tables if table.name not in (PUBLICATIONS, self.values.table))
