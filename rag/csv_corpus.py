import csv
import json
from collections.abc import Iterator
from pathlib import Path

from config import MODULE_DIR
from evidence import Fragment

SCHEMA_PATH = MODULE_DIR / "csv_schema.json"
RESULT_LINKS = {
    "config_id": "configurations",
    "dataset_id": "datasets",
    "task_id": "tasks",
    "experiment_id": "experiments",
}
SEARCH_FIELDS = {
    "publications": ("title", "authors", "year", "DOI"),
    "configurations": ("rag_variant", "retrieval_method", "generator_model", "retriever_model", "embedding_model"),
    "datasets": ("dataset_name", "data_split", "language"),
    "tasks": ("task_type",),
    "experiments": (),
    "results": ("metric_name", "metric_variant", "metric_value", "source_table", "source_figure"),
    "issues": ("check_name",),
}

type Row = dict[str, str]
type Table = dict[tuple[str, str], Row]


def read_table(path: Path, columns: list[str], key_column: str) -> Table:
    table = {}
    with path.open(encoding="utf-8-sig", newline="") as file:
        reader = csv.DictReader(file, strict=True)
        if reader.fieldnames != columns:
            raise ValueError(f"{path.name}: колонки не совпадают со схемой")
        for row in reader:
            if None in row or any(value is None for value in row.values()):
                raise ValueError(f"{path.name}:{reader.line_num}: неверное число полей")
            fields = {name: value.strip() for name, value in row.items()}
            key = (fields["publication_id"], fields[key_column])
            if not all(key) or key in table:
                raise ValueError(f"{path.name}:{reader.line_num}: пустой или повторяющийся ключ")
            table[key] = fields
    return table


def load_csv_corpus(directory: Path) -> Iterator[Fragment]:
    schema = read_schema()
    tables = {
        name: read_table(directory / f"{name}.csv", columns, schema["keys"][name])
        for name, columns in schema["tables"].items()
    }
    for table_name, table in tables.items():
        for (publication, identifier), row in table.items():
            if (publication, publication) not in tables["publications"]:
                raise ValueError(f"{table_name}.csv ({publication}/{identifier}): публикация отсутствует")
            linked = {"publications": tables["publications"][publication, publication]}
            if table_name == "results":
                for field, target in RESULT_LINKS.items():
                    link = row[field]
                    if link:
                        if (publication, link) not in tables[target]:
                            raise ValueError(f"{publication}/{identifier}: отсутствует {target}/{link}")
                        linked[target] = tables[target][publication, link]
            yield row_fragment(table_name, publication, identifier, row, linked)


def read_schema() -> dict:
    return json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))


def row_fragment(table: str, publication: str, identifier: str, row: Row, linked: dict[str, Row]) -> Fragment:
    location = publication if table == "publications" else f"{publication}/{identifier}"
    reference = f"{table}.csv ({location})"
    if locator := row.get("source_table") or row.get("source_figure"):
        reference = f"{reference}, {locator}"
    parts = [format_fields(table, row)]
    for name, fields in linked.items():
        if name != table:
            parts.append(format_fields(name, {key: value for key, value in fields.items() if key != "note"}))
    tags = [publication, identifier, table]
    tags.extend(row[field] for field in SEARCH_FIELDS[table])
    for name, fields in linked.items():
        if name not in {table, "publications"}:
            tags.extend(fields[field] for field in SEARCH_FIELDS[name])
    page = int(row["source_page"]) if row.get("source_page") else None
    return Fragment(reference, page, "\n\n".join(parts), " ".join(filter(None, tags)))


def format_fields(table: str, row: Row) -> str:
    fields = "\n".join(f"{name}: {value}" for name, value in row.items() if value)
    return f"{table}.csv\n{fields}"
