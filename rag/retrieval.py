import re
from collections import defaultdict

from qdrant_client import QdrantClient, models

from config import collection_name, get_top_k
from csv_corpus import LABEL_FIELDS
from evidence import Fragment, normalize_label, publication_ids, source_locations
from vector_store import check_collection, database, fragment_from_payload
from vectors import query_vector

REFERENCE_ID = re.compile(r"\b[DP]\d{3}/[A-Z][\w-]*\b", re.IGNORECASE)
RECORD_ID = re.compile(
    r"\b(?:запис\w*|конфигурац\w*|record|configuration)\s*(?:№\s*)?([A-Z][A-Z0-9_-]*\d+)\b(?!/)",
    re.IGNORECASE,
)
TABLE_NAME = re.compile(r"\b(\w+)\.csv\b", re.IGNORECASE)
PAGE = re.compile(r"\b(?:стр\.?|страниц\w*|page)\s*(\d+)\b", re.IGNORECASE)
NAMED_VALUE = re.compile(r'«([^»\n]+)»|“([^”\n]+)”|"([^"\n]+)"|\(([^()\n]+)\)')
METRIC_K = re.compile(r"\bk\s*=\s*(\d+)\b", re.IGNORECASE)


def search(query: str, k: int | None = None, *, terms: str = "") -> list[Fragment]:
    return retrieve("pdf", query, f"{query} {terms}".strip(), get_top_k() if k is None else k)


def search_csv(query: str, phrases: list[str], k: int | None = None) -> list[Fragment]:
    terms = " ".join(dict.fromkeys(phrases))
    return retrieve("csv", query, f"{query} {terms}".strip(), get_top_k(8) if k is None else k)


def retrieval_filter(query: str, kind: str) -> models.Filter | None:
    fields = {"publication_id": publication_ids(query)}
    if kind in {"csv", "pdf"}:
        fields["page"] = [int(value) for value in PAGE.findall(query)]
    if kind == "csv":
        fields["locations"] = source_locations(query)
        fields["reference_id"] = [value.upper() for value in REFERENCE_ID.findall(query)]
        fields["record_id"] = [value.upper() for value in RECORD_ID.findall(query)]
        fields["table_name"] = [value.lower() for value in TABLE_NAME.findall(query)]
    conditions = [
        models.FieldCondition(key=field, match=models.MatchAny(any=values))
        for field, values in fields.items() if values
    ]
    return models.Filter(must=conditions) if conditions else None


def named_values_filter(client: QdrantClient, name: str, question: str, scope: models.Filter | None) -> models.Filter | None:
    names = {normalize_label(next(value for value in match if value)) for match in NAMED_VALUE.findall(question)}
    requested = {
        f"{field}={value}"
        for fields in LABEL_FIELDS.values()
        for field in fields if field != "metric_k"
        for value in names if value
    }
    requested.update(f"metric_k={value}" for value in METRIC_K.findall(question))
    if not requested:
        return None

    candidates = label_condition(sorted(requested))
    facet_scope = models.Filter(must=[scope, candidates] if scope else [candidates])
    facets = client.facet(name, "search_labels", facet_filter=facet_scope, limit=512, exact=True)
    fields_by_name: dict[str, list[str]] = defaultdict(list)
    for key in sorted(requested.intersection(hit.value for hit in facets.hits)):
        field, value = key.split("=", 1)
        fields_by_name[value].append(field)

    grouped: dict[str, list[str]] = defaultdict(list)
    for value, fields in fields_by_name.items():
        if len(fields) == 1:
            grouped[fields[0]].append(f"{fields[0]}={value}")
    if not grouped:
        return None
    conditions = [label_condition(labels) for labels in grouped.values()]
    return models.Filter(must=[scope, *conditions] if scope else conditions)


def label_condition(values: list[str]) -> models.FieldCondition:
    return models.FieldCondition(key="search_labels", match=models.MatchAny(any=values))


def retrieve(kind: str, question: str, query: str, limit: int) -> list[Fragment]:
    if limit < 1:
        raise ValueError("TOP_K должен быть положительным числом")
    if not query.strip():
        return []

    main = collection_name(kind)
    approved = collection_name("approved")
    with database() as client:
        if not client.collection_exists(main):
            flag = " --csv" if kind == "csv" else ""
            raise ValueError(f"Сначала создайте индекс командой index.py{flag}")
        info = check_collection(client, main)
        vector = query_vector(query)
        scope = retrieval_filter(question, kind)
        direct_reference = kind == "csv" and bool(REFERENCE_ID.search(question) or RECORD_ID.search(question))
        strict_scope = None
        if kind == "csv" and "search_labels" in info.payload_schema and not direct_reference:
            strict_scope = named_values_filter(client, main, question, scope)
        hits = client.query_points(main, query=vector, query_filter=strict_scope or scope, limit=limit).points
        if strict_scope is not None and not hits:
            hits = client.query_points(main, query=vector, query_filter=scope, limit=limit).points
        if not direct_reference and client.collection_exists(approved):
            check_collection(client, approved)
            web_hits = client.query_points(
                approved, query=vector, query_filter=retrieval_filter(question, "approved"), limit=limit,
            ).points
            hits = sorted([*hits, *web_hits], key=lambda hit: hit.score, reverse=True)[:limit]
    return [fragment_from_payload(hit.payload) for hit in hits if hit.payload]
