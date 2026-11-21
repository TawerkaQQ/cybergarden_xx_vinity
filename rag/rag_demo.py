import os
from dotenv import load_dotenv

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_ollama import ChatOllama, OllamaEmbeddings
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from langchain_qdrant import QdrantVectorStore
from qdrant_client import QdrantClient
from qdrant_client.http.models import Distance, VectorParams

load_dotenv()
LLM_PROVIDER = os.getenv("LLM_PROVIDER", "ollama")

client = QdrantClient(url="http://localhost:6333")

if LLM_PROVIDER == "ollama":
    base_url = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
    llm_model = os.getenv("OLLAMA_LLM_MODEL", "llama3.2:1b")
    emb_model = os.getenv("OLLAMA_EMBEDDING_MODEL", "nomic-embed-text")
    emb_dim = int(os.getenv("OLLAMA_EMBEDDING_DIMENSION", "768"))
    embeddings = OllamaEmbeddings(model=emb_model, base_url=base_url)
    llm = ChatOllama(model=llm_model, base_url=base_url, temperature=0)
else:
    base_url = os.getenv("REMOTE_BASE_URL")
    api_key = os.getenv("REMOTE_API_KEY", "not-needed")
    llm_model = os.getenv("REMOTE_LLM_MODEL")
    emb_model = os.getenv("REMOTE_EMBEDDING_MODEL")
    emb_dim = int(os.getenv("REMOTE_EMBEDDING_DIMENSION"))
    embeddings = OpenAIEmbeddings(
        model=emb_model,
        base_url=base_url,
        api_key=api_key,
        check_embedding_ctx_length=False,  # важно для не-OpenAI серверов
    )
    llm = ChatOpenAI(model=llm_model, base_url=base_url, api_key=api_key, temperature=0)

# --- Коллекция привязана к модели эмбеддингов ---
collection_name = f"docs_{emb_model.replace(':', '_').replace('/', '_')}"

if not client.collection_exists(collection_name):
    client.create_collection(
        collection_name=collection_name,
        vectors_config=VectorParams(size=emb_dim, distance=Distance.COSINE),
    )

raw_texts = [
    "Qdrant — это векторная база данных, написанная на Rust.",
    "RAG означает Retrieval-Augmented Generation: сначала поиск, потом генерация.",
    "LangChain помогает связывать LLM с внешними источниками данных.",
    "Векторное представление текста называется эмбеддингом.",
]
documents = [Document(page_content=t) for t in raw_texts]

splitter = RecursiveCharacterTextSplitter(chunk_size=500, chunk_overlap=100)
chunks = splitter.split_documents(documents)

vectorstore = QdrantVectorStore(
    client=client,
    collection_name=collection_name,
    embedding=embeddings,
)
vectorstore.add_documents(chunks)
print(f"Записано чанков: {len(chunks)} в '{collection_name}' (провайдер: {LLM_PROVIDER})")

# --- Поиск и генерация ---
query = "Что такое RAG?"
results = vectorstore.similarity_search(query, k=2)

print("\n--- Найденные фрагменты ---")
for r in results:
    print(f"- {r.page_content}")

context = "\n\n".join([r.page_content for r in results])
prompt = f"""Ответь на вопрос, используя ТОЛЬКО контекст ниже.

Контекст:
{context}

Вопрос: {query}
"""
answer = llm.invoke(prompt)
print("\n--- Ответ ---")
print(answer.content)