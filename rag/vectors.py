import os
from collections.abc import Iterable, Iterator
from functools import lru_cache

from config import QDRANT_DIR

DEFAULT_PROFILE = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"


def embedding_profile() -> str:
    return os.getenv("EMBEDDING_PROFILE", "").strip() or DEFAULT_PROFILE


@lru_cache(maxsize=1)
def encoder(profile: str):
    from fastembed import TextEmbedding

    return TextEmbedding(profile, cache_dir=str(QDRANT_DIR / "embeddings"), threads=min(8, os.cpu_count() or 1))


def embedding_size() -> int:
    from fastembed import TextEmbedding

    return TextEmbedding.get_embedding_size(embedding_profile())


def passage_vectors(texts: Iterable[str]) -> Iterator[list[float]]:
    for vector in encoder(embedding_profile()).passage_embed(texts, batch_size=64):
        yield vector.tolist()


def query_vector(text: str) -> list[float]:
    return next(iter(encoder(embedding_profile()).query_embed(text))).tolist()
