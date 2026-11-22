import os
from collections.abc import Iterable, Iterator
from contextlib import closing, contextmanager
from itertools import batched
from uuid import uuid4

from qdrant_client import QdrantClient, models
from qdrant_client.http.exceptions import ResponseHandlingException, UnexpectedResponse

from config import collection_name, get_qdrant_url
from evidence import Fragment, normalize_label, source_locations
from vectors import embedding_profile, embedding_size, passage_vectors

BATCH_SIZE = 256


@contextmanager
def database() -> Iterator[QdrantClient]:
    token = os.getenv("QDRANT_API_KEY", "").strip() or None
    try:
        with closing(QdrantClient(url=get_qdrant_url(), api_key=token, timeout=30)) as client:
            yield client
    except ResponseHandlingException as error:
        raise OSError("Не удалось подключиться к Qdrant. Проверьте QDRANT_URL") from error
    except UnexpectedResponse as error:
        raise OSError(f"Qdrant вернул HTTP {error.status_code}") from error


def create_collection(client: QdrantClient, name: str, *, csv_labels: bool = False) -> None:
    client.create_collection(
        name,
        vectors_config=models.VectorParams(size=embedding_size(), distance=models.Distance.COSINE),
        metadata={"embedding_profile": embedding_profile()},
    )
    for field in ("publication_id", "record_id", "reference_id", "locations", "table_name"):
        client.create_payload_index(name, field, models.PayloadSchemaType.KEYWORD, wait=True)
    client.create_payload_index(name, "page", models.PayloadSchemaType.INTEGER, wait=True)
    if csv_labels:
        client.create_payload_index(name, "search_labels", models.PayloadSchemaType.KEYWORD, wait=True)


def check_collection(client: QdrantClient, name: str) -> models.CollectionInfo:
    info = client.get_collection(name)
    config = info.config
    params = config.params.vectors
    metadata = config.metadata or {}
    if not isinstance(params, models.VectorParams) or params.distance != models.Distance.COSINE:
        raise ValueError(f"Коллекция {name} должна использовать косинусную метрику")
    if metadata.get("embedding_profile") != embedding_profile() or params.size != embedding_size():
        raise ValueError(f"Коллекция {name} создана с другим профилем эмбеддингов")
    return info


def fragment_payload(fragment: Fragment) -> dict:
    publication = fragment.publication_id.upper()
    record = fragment.record_id.upper()
    file = fragment.source.split(" ", 1)[0]
    return {
        "source": fragment.source,
        "page": fragment.page,
        "text": fragment.text,
        "keywords": fragment.keywords,
        "publication_id": publication,
        "record_id": record,
        "reference_id": f"{publication}/{record}" if publication and record else "",
        "locations": source_locations(fragment.locator),
        "locator": fragment.locator,
        "table_name": file.removesuffix(".csv") if file.endswith(".csv") else "",
        "search_labels": [f"{field}={normalize_label(value)}" for field, value in fragment.search_labels],
    }


def fragment_from_payload(payload: dict) -> Fragment:
    return Fragment(payload["source"], payload.get("page"), payload["text"], payload.get("keywords", ""))


def upload_fragments(client: QdrantClient, name: str, fragments: Iterable[Fragment]) -> int:
    count = 0
    next_message = 5000
    cache: dict[str, list[float]] = {}
    for batch in batched(fragments, BATCH_SIZE):
        texts = [fragment.keywords or fragment.text for fragment in batch]
        missing = list(dict.fromkeys(text for text in texts if text not in cache))
        if missing:
            cache.update(zip(missing, passage_vectors(missing), strict=True))
        new_count = count + len(batch)
        points = models.Batch(
            ids=list(range(count + 1, new_count + 1)),
            vectors=[cache[text] for text in texts],
            payloads=[fragment_payload(fragment) for fragment in batch],
        )
        client.upsert(name, points, wait=True)
        count = new_count
        if count >= next_message:
            print(f"Записано {count} фрагментов, вычислено {len(cache)} векторов", flush=True)
            next_message += 5000
    return count


def replace_index(kind: str, fragments: Iterable[Fragment]) -> int:
    alias = collection_name(kind)
    name = f"{alias}_build_{uuid4().hex}"
    with database() as client:
        create_collection(client, name, csv_labels=kind == "csv")
        try:
            count = upload_fragments(client, name, fragments)
            if not count:
                raise ValueError("Не найдено записей для индексации")
            if client.count(name, exact=True).count != count:
                raise OSError("Количество записей в индексе не совпало с количеством фрагментов")
        except Exception:
            client.delete_collection(name)
            raise
        publish_collection(client, alias, name)
    return count


def publish_collection(client: QdrantClient, alias: str, name: str) -> None:
    aliases = {item.alias_name: item.collection_name for item in client.get_aliases().aliases}
    previous = aliases.get(alias)
    actions = []
    if previous:
        actions.append(models.DeleteAliasOperation(delete_alias=models.DeleteAlias(alias_name=alias)))
    actions.append(models.CreateAliasOperation(create_alias=models.CreateAlias(collection_name=name, alias_name=alias)))
    client.update_collection_aliases(actions)
    if previous and previous.startswith(f"{alias}_build_"):
        references = {item.collection_name for item in client.get_aliases().aliases}
        if previous not in references:
            client.delete_collection(previous)
