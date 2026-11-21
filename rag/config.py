import json
import os
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit

from dotenv import dotenv_values, load_dotenv

MODULE_DIR = Path(__file__).resolve().parent
LOCAL_ENV = MODULE_DIR / ".env"
WORKSPACE_DATA = MODULE_DIR.parent / "data" / "rag"
ENV_FILES = (LOCAL_ENV, MODULE_DIR.parent / ".env") if WORKSPACE_DATA.is_dir() else (LOCAL_ENV,)
for env_file in ENV_FILES:
    load_dotenv(env_file)


def data_directory() -> Path:
    if setting := os.getenv("RAG_DATA_DIR", "").strip():
        return (MODULE_DIR / Path(setting).expanduser()).resolve()
    local = MODULE_DIR / "data"
    return WORKSPACE_DATA if WORKSPACE_DATA.is_dir() and not local.is_dir() else local


DATA_DIR = data_directory()
INDEX_PATH = MODULE_DIR / ".index.sqlite3"
CSV_INDEX_PATH = INDEX_PATH.with_name(".csv-index.sqlite3")
APPROVED_WEB_PATH = INDEX_PATH.with_name(".approved-web.sqlite3")


@dataclass(frozen=True, repr=False)
class ChatSettings:
    url: str
    profile: str
    token: str
    options: dict[str, object]


def get_searxng_url() -> str:
    return os.getenv("SEARXNG_URL", "").strip().rstrip("/")


def get_chat_settings() -> ChatSettings:
    base_url = os.getenv("CHAT_BASE_URL", "").strip().rstrip("/")
    profile = os.getenv("CHAT_PROFILE", "").strip()
    token = os.getenv("CHAT_API_KEY", "").strip()
    options = {}
    if not base_url and not profile:
        prefix = configured_profile_prefix()
        if prefix:
            base_url = os.getenv(f"{prefix}_BASE_URL", "").strip().rstrip("/")
            profile = os.getenv(f"{prefix}_MODEL", "").strip()
            token = os.getenv(f"{prefix}_API_KEY", "").strip()
            options = {"chat_template_kwargs": {"enable_thinking": False}}

    url = urlsplit(base_url)
    if url.scheme not in {"http", "https"} or not url.hostname or url.query or url.fragment:
        raise ValueError("Укажите адрес сервиса в CHAT_BASE_URL")
    if not profile:
        raise ValueError("Укажите профиль сервиса в CHAT_PROFILE")
    if raw_options := os.getenv("CHAT_REQUEST_OPTIONS", "").strip():
        try:
            options = json.loads(raw_options)
        except json.JSONDecodeError as error:
            raise ValueError("CHAT_REQUEST_OPTIONS должен содержать объект JSON") from error
        if not isinstance(options, dict):
            raise ValueError("CHAT_REQUEST_OPTIONS должен содержать объект JSON")
    if not url.path:
        base_url += "/v1"
    endpoint = base_url.removesuffix("/chat/completions") + "/chat/completions"
    return ChatSettings(endpoint, profile, token, options)


def configured_profile_prefix() -> str | None:
    declared = {key for path in ENV_FILES for key in dotenv_values(path)}
    candidates = (key.removesuffix("_BASE_URL") for key in declared if key.endswith("_BASE_URL"))
    prefixes = {
        prefix for prefix in candidates if prefix != "CHAT" and f"{prefix}_MODEL" in declared
        and os.getenv(f"{prefix}_BASE_URL") and os.getenv(f"{prefix}_MODEL")
    }
    if len(prefixes) > 1:
        raise ValueError("В .env несколько профилей. Укажите CHAT_BASE_URL и CHAT_PROFILE")
    return next(iter(prefixes), None)


def get_top_k(default: int = 3) -> int:
    return int(os.getenv("TOP_K", str(default)))
