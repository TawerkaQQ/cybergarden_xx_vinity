from dataclasses import dataclass
from typing import Any

from pipeline.domain.extract import ExtractResult
from pipeline.domain.numbers import parse_number
from pipeline.domain.profile import PUBLICATION_ID, Profile, Table

IN_SCOPE = "in_scope"
MENTION_KEYS = {
    "metric": "metric",
    "raw_value": "raw_value",
    "raw_unit": "raw_unit",
    "origin": "origin",
    "cited": "cited",
    "status": "status",
    "page": "page",
    "section": "section",
    "table": "table",
    "figure": "figure",
    "fragment": "quote",
    "note": "note",
}
ASSEMBLER_ROLES = ("id", "value", "unit", "duplicate_of")
CONTRACT_MENTION_ORDER = (
    "metric", "raw_value", "raw_unit", "refs", "fields", "unit", "origin", "cited",
    "status", "page", "section", "table", "figure", "quote", "note",
)
NULLABLE_STRING = {"type": ["string", "null"]}
NULLABLE_VALUE = {"type": ["string", "number", "null"]}
STRING = {"type": "string"}
PAGE = {"type": ["integer", "string"]}
VERBATIM_MENTION_KEYS = ("page", "refs", "fields")


def strict_object(properties: dict[str, Any]) -> dict[str, Any]:
    return {"type": "object", "properties": properties, "required": list(properties), "additionalProperties": False}


def column_schema(table: Table, column: str) -> dict[str, Any]:
    if column in table.vocabularies and column not in table.unit_columns:
        return {"enum": [*table.vocabularies[column], None]}
    return NULLABLE_VALUE


def as_text(value: Any) -> Any:
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return str(value)
    return value


def texts(values: dict[str, Any]) -> dict[str, Any]:
    return {name: as_text(value) for name, value in values.items()}


def ref_schema(table: Table, column: str) -> dict[str, Any]:
    if column in table.lists:
        return {"type": "array", "items": STRING}
    return NULLABLE_STRING


@dataclass(frozen=True)
class RecordShape:
    table: Table
    fields: tuple[str, ...]
    refs: dict[str, str]

    def schema(self) -> dict[str, Any]:
        return strict_object(
            {
                "key": STRING,
                "fields": strict_object({column: column_schema(self.table, column) for column in self.fields}),
                "refs": strict_object({column: ref_schema(self.table, column) for column in self.refs}),
            }
        )


@dataclass(frozen=True)
class MentionShape:
    table: Table
    columns: dict[str, str]
    fields: tuple[str, ...]
    refs: dict[str, str]

    def schema(self) -> dict[str, Any]:
        return strict_object(
            {
                "page": PAGE,
                "section": NULLABLE_VALUE,
                "table": NULLABLE_VALUE,
                "figure": NULLABLE_VALUE,
                "quote": STRING,
                "metric": STRING,
                "raw_value": NULLABLE_VALUE,
                "raw_unit": NULLABLE_VALUE,
                "unit": NULLABLE_VALUE,
                "fields": strict_object({column: column_schema(self.table, column) for column in self.fields}),
                "refs": strict_object({column: ref_schema(self.table, column) for column in self.refs}),
                "origin": self._required_vocabulary("origin"),
                "cited": NULLABLE_VALUE,
                "status": self._required_vocabulary("status"),
                "note": NULLABLE_VALUE,
            }
        )

    def vocabulary(self, key: str) -> tuple[str, ...]:
        return self.table.vocabularies.get(self.columns.get(key, ""), ())

    def _required_vocabulary(self, key: str) -> dict[str, Any]:
        values = self.vocabulary(key)
        return {"enum": list(values)} if values else STRING


class ExtractionShape:
    def __init__(self, profile: Profile):
        self.profile = profile
        self.publication_columns = tuple(
            column for column in profile.publications.columns if column != profile.publications.id
        )
        self.records = tuple(self._record_shape(table) for table in profile.record_tables)
        self.mention = self._mention_shape(profile.values_table)

    def structure_schema(self) -> dict[str, Any]:
        publication = strict_object(
            {
                column: {"type": "boolean"} if column == IN_SCOPE else column_schema(self.profile.publications, column)
                for column in self.publication_columns
            }
        )
        records = strict_object({shape.table.name: {"type": "array", "items": shape.schema()} for shape in self.records})
        return strict_object({"publication": publication, "records": records})

    def mentions_schema(self) -> dict[str, Any]:
        return strict_object({"mentions": {"type": "array", "items": self.mention.schema()}})

    @staticmethod
    def tables_schema() -> dict[str, Any]:
        table = strict_object(
            {
                "table": STRING,
                "page": PAGE,
                "columns": {"type": "array", "items": STRING},
                "rows": {"type": "array", "items": STRING},
            }
        )
        return strict_object({"tables": {"type": "array", "items": table}})

    def result(
        self, publication_id: str, prompt_version: str, structure: dict[str, Any], mentions: dict[str, Any]
    ) -> ExtractResult:
        publication = texts(structure["publication"])
        in_scope = bool(publication.get(IN_SCOPE))
        records = {
            shape.table.name: [{**record, "fields": texts(record["fields"])} for record in structure["records"][shape.table.name]]
            if in_scope
            else []
            for shape in self.records
        }
        keys = {table: {record["key"] for record in items} for table, items in records.items()}
        linked_records = {
            shape.table.name: [self._linked(record, shape.refs, keys) for record in records[shape.table.name]]
            for shape in self.records
        }
        linked_mentions = [
            self._contract_mention(self._linked(mention, self.mention.refs, keys))
            for mention in (mentions["mentions"] if in_scope else [])
        ]
        return ExtractResult(
            publication_id=publication_id,
            profile=self.profile.name,
            prompt_version=prompt_version,
            publication=publication,
            records=linked_records,
            mentions=linked_mentions,
        )

    def _record_shape(self, table: Table) -> RecordShape:
        refs = self._refs(table)
        skipped = {PUBLICATION_ID, table.id, *refs}
        return RecordShape(table, tuple(column for column in table.columns if column not in skipped), refs)

    def _mention_shape(self, table: Table) -> MentionShape:
        roles = self.profile.values.roles
        refs = self._refs(table)
        contract_columns = {roles[role] for role in (*MENTION_KEYS, *ASSEMBLER_ROLES) if role in roles}
        skipped = {PUBLICATION_ID, table.id, *refs, *contract_columns, *self.profile.values.mirrors}
        columns = {MENTION_KEYS[role]: column for role, column in roles.items() if role in MENTION_KEYS}
        return MentionShape(table, columns, tuple(column for column in table.columns if column not in skipped), refs)

    def _refs(self, table: Table) -> dict[str, str]:
        record_tables = {record.name for record in self.profile.record_tables}
        return {column: target for column, target in table.references.items() if target in record_tables}

    @staticmethod
    def _linked(item: dict[str, Any], refs: dict[str, str], keys: dict[str, set[str]]) -> dict[str, Any]:
        linked = {}
        for column, target in refs.items():
            value = item["refs"].get(column)
            if isinstance(value, list):
                linked[column] = [key for key in value if key in keys[target]]
            else:
                linked[column] = value if value in keys[target] else None
        return {**item, "refs": linked}

    @staticmethod
    def _contract_mention(mention: dict[str, Any]) -> dict[str, Any]:
        contract = {key: mention[key] if key in VERBATIM_MENTION_KEYS else as_text(mention[key]) for key in CONTRACT_MENTION_ORDER}
        return {**contract, "fields": texts(mention["fields"])}


def page_number(value: Any) -> int | None:
    number = parse_number(as_text(value))
    return int(number) if number is not None and number >= 1 else None


def with_page_number(item: dict[str, Any]) -> dict[str, Any]:
    return {**item, "page": page_number(item.get("page"))}
