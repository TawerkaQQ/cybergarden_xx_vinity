from datetime import UTC, datetime
from uuid import NAMESPACE_URL, uuid5

from qdrant_client import models
from qdrant_client.http.exceptions import UnexpectedResponse

from config import collection_name
from evidence import WebPassage, publication_ids
from vector_store import check_collection, create_collection, database
from vectors import passage_vectors


def save_passages(question: str, passages: list[WebPassage]) -> int:
    if not passages:
        return 0

    name = collection_name("approved")
    approved_at = datetime.now(UTC).isoformat(timespec="seconds")
    by_id = {str(uuid5(NAMESPACE_URL, f"{item.url}\n{item.text}")): item for item in passages}
    with database() as client:
        if not client.collection_exists(name):
            try:
                create_collection(client, name)
            except UnexpectedResponse:
                if not client.collection_exists(name):
                    raise
        check_collection(client, name)
        saved = client.retrieve(name, list(by_id), with_payload=False)
        existing = {str(item.id) for item in saved}
        pending = [(key, item) for key, item in by_id.items() if key not in existing]
        if not pending:
            return 0
        texts = (f"{item.title} {item.text} {question}" for _, item in pending)
        points = [
            models.PointStruct(
                id=key,
                vector=vector,
                payload={
                    "source": f"Одобренный веб-источник. {item.reference}",
                    "text": item.text,
                    "publication_id": publication_ids(question),
                    "url": item.url,
                    "title": item.title,
                    "fetched_at": item.fetched_at,
                    "approved_at": approved_at,
                },
            )
            for (key, item), vector in zip(pending, passage_vectors(texts), strict=True)
        ]
        client.upsert(name, points, wait=True, update_mode=models.UpdateMode.INSERT_ONLY)
    return len(pending)
