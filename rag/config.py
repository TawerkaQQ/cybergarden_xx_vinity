import os
from pathlib import Path
from dotenv import load_dotenv
from langchain_ollama import ChatOllama, OllamaEmbeddings
from langchain_openai import ChatOpenAI, OpenAIEmbeddings

load_dotenv()

LLM_PROVIDER = os.getenv("LLM_PROVIDER", "ollama")

PROJECT_ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = PROJECT_ROOT / "data" / "rag"
QDRANT_URL = os.getenv("QDRANT_URL")

def get_embeddings():
    if LLM_PROVIDER == "ollama":
        return OllamaEmbeddings(
            model=os.getenv("OLLAMA_EMBEDDING_MODEL", "nomic-embed-text"),
            base_url=os.getenv("OLLAMA_BASE_URL", "http://localhost:11434"),
        )
    return OpenAIEmbeddings(
        model=os.getenv("REMOTE_EMBEDDING_MODEL"),
        base_url=os.getenv("REMOTE_BASE_URL"),
        api_key=os.getenv("REMOTE_API_KEY", "not-needed"),
        check_embedding_ctx_length=False,
    )

def get_llm():
    if LLM_PROVIDER == "ollama":
        return ChatOllama(
            model=os.getenv("OLLAMA_LLM_MODEL", "llama3.2:1b"),
            base_url=os.getenv("OLLAMA_BASE_URL", "http://localhost:11434"),
            temperature=0,
        )
    return ChatOpenAI(
        model=os.getenv("REMOTE_LLM_MODEL"),
        base_url=os.getenv("REMOTE_BASE_URL"),
        api_key=os.getenv("REMOTE_API_KEY", "not-needed"),
        temperature=0,
    )

def get_embedding_dim() -> int:
    if LLM_PROVIDER == "ollama":
        return int(os.getenv("OLLAMA_EMBEDDING_DIMENSION", "768"))
    return int(os.getenv("REMOTE_EMBEDDING_DIMENSION"))

def get_embedding_model_name() -> str:
    if LLM_PROVIDER == "ollama":
        return os.getenv("OLLAMA_EMBEDDING_MODEL", "nomic-embed-text")
    return os.getenv("REMOTE_EMBEDDING_MODEL")

def get_chunk_size() -> int:
    return int(os.getenv("CHUNK_SIZE", "1000"))

def get_chunk_overlap() -> int:
    return int(os.getenv("CHUNK_OVERLAP", "200"))

def get_top_k() -> int:
    return int(os.getenv("TOP_K", "3"))