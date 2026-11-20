from dataclasses import dataclass
from typing import Any

PUBLICATIONS = "publications"
PUBLICATION_ID = "publication_id"


@dataclass(frozen=True)
class Table:
    name: str
    id: str
    columns: tuple[str, ...]
    references: dict[str, str]
    lists: tuple[str, ...]
    vocabularies: dict[str, tuple[str, ...]]
    unit_columns: tuple[str, ...]
    descriptions: dict[str, str]

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "Table":
        return cls(
            name=payload["name"],
            id=payload["id"],
            columns=tuple(payload["columns"]),
            references=dict(payload.get("references", {})),
            lists=tuple(payload.get("lists", [])),
            vocabularies={column: tuple(values) for column, values in payload.get("vocabularies", {}).items()},
            unit_columns=tuple(unit["unit_column"] for unit in payload.get("units", {}).values()),
            descriptions=dict(payload.get("descriptions", {})),
        )


@dataclass(frozen=True)
class ValuesSpec:
    table: str
    roles: dict[str, str]
    mirrors: tuple[str, ...]
    metrics: dict[str, str]

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "ValuesSpec":
        return cls(
            table=payload["table"],
            roles=dict(payload["columns"]),
            mirrors=tuple(payload.get("mirrors", {})),
            metrics=dict(payload.get("metrics", {})),
        )


@dataclass(frozen=True)
class Profile:
    name: str
    title: str
    scope: str
    tables: tuple[Table, ...]
    values: ValuesSpec
    table_fragment: str
    rules: str

    @classmethod
    def from_json(cls, payload: dict[str, Any], rules: str) -> "Profile":
        return cls(
            name=payload["name"],
            title=payload["title"],
            scope=payload["scope"],
            tables=tuple(Table.from_json(table) for table in payload["tables"]),
            values=ValuesSpec.from_json(payload["values"]),
            table_fragment=payload.get("sources", {}).get("table_fragment", "literal"),
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
