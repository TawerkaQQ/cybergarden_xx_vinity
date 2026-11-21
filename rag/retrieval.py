from qdrant_client import QdrantClient
from langchain_qdrant import QdrantVectorStore
from config import get_embeddings, get_top_k
from ingestion import get_collection_name, QDRANT_URL

def search(query: str, k: int | None = None):
    client = QdrantClient(url=QDRANT_URL)
    vectorstore = QdrantVectorStore(
        client=client,
        collection_name=get_collection_name(),
        embedding=get_embeddings(),
    )
    return vectorstore.similarity_search(query, k=k or get_top_k())