from pathlib import Path
from qdrant_client import QdrantClient
from qdrant_client.http.models import Distance, VectorParams
from langchain_qdrant import QdrantVectorStore
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_community.document_loaders import PyPDFLoader
from config import (
    get_embeddings, get_embedding_dim, get_embedding_model_name,
    get_chunk_size, get_chunk_overlap, QDRANT_URL, DATA_DIR,
)

def get_collection_name() -> str:
    model = get_embedding_model_name().replace(":", "_").replace("/", "_")
    return f"docs_{model}"

def load_pdfs_from_data_dir() -> list:
    """Загружает все PDF из data/rag/ и возвращает список Document."""
    pdf_files = sorted(DATA_DIR.glob("*.pdf"))
    if not pdf_files:
        raise FileNotFoundError(f"В директории {DATA_DIR} не найдено PDF-файлов")

    documents = []
    for pdf_path in pdf_files:
        print(f"Загружаю: {pdf_path.name}")
        documents.extend(PyPDFLoader(str(pdf_path)).load())
    return documents

def ingest(documents):
    client = QdrantClient(url=QDRANT_URL)
    collection = get_collection_name()

    if client.collection_exists(collection): # удаление перед заполнением
        client.delete_collection(collection)
    client.create_collection(
        collection_name=collection,
        vectors_config=VectorParams(size=get_embedding_dim(), distance=Distance.COSINE),
    )

    splitter = RecursiveCharacterTextSplitter(
        chunk_size=get_chunk_size(),
        chunk_overlap=get_chunk_overlap(),
    )
    chunks = splitter.split_documents(documents)

    vectorstore = QdrantVectorStore(
        client=client,
        collection_name=collection,
        embedding=get_embeddings(),
    )
    vectorstore.add_documents(chunks)
    print(f"Записано чанков: {len(chunks)} в '{collection}'")
    return len(chunks)