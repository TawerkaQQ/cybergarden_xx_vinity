"""JSON-интерфейс модуля для сервера Vinity: одна команда — одна строка JSON в stdout."""

import argparse
import json
import sys

from config import collection_name, get_chat_settings, get_qdrant_url, get_searxng_url
from evidence import publication_ids


def main() -> None:
    parser = argparse.ArgumentParser(description="Вопросы к модулю поиска в формате JSON")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("status", help="состояние базы и сервиса ответов")
    ask = commands.add_parser("ask", help="ответ по локальной базе")
    ask.add_argument("--csv", action="store_true", help="искать по таблицам CSV")
    ask.add_argument("--top-k", type=int, help="сколько фрагментов передать модели")
    ask.add_argument("question", nargs="?", help="без вопроса запрос читается из stdin в JSON")
    web = commands.add_parser("web", help="ответ по открытым источникам")
    web.add_argument("--phrase", action="append", default=[], help="поисковая фраза, можно несколько")
    web.add_argument("question", nargs="?", help="без вопроса запрос читается из stdin в JSON")
    args = parser.parse_args()
    try:
        if args.command == "status":
            result = status()
        else:
            request = {} if args.question is not None else json.loads(sys.stdin.buffer.read().decode("utf-8") or "{}")
            question = str(args.question if args.question is not None else request.get("question", "")).strip()
            if args.command == "ask":
                top_k = args.top_k if args.top_k is not None else request.get("top_k")
                result = answer_from_base(question, args.csv or bool(request.get("csv")), top_k)
            else:
                result = answer_from_web(question, args.phrase or list(request.get("phrases") or []))
    except Exception as error:  # сервер показывает текст ошибки пользователю
        emit({"error": describe(error)})
        sys.exit(1)
    emit(result)


def status() -> dict:
    from vectors import embedding_profile

    result: dict = {
        "qdrant": None,
        "collections": {},
        "problems": [],
        "embedding": embedding_profile(),
        "web_search": "SearXNG" if get_searxng_url() else "DuckDuckGo",
    }
    try:
        settings = get_chat_settings()
        result["model"] = settings.profile
    except ValueError as error:
        result["problems"].append(str(error))
    try:
        result["qdrant"] = get_qdrant_url()
        from vector_store import database

        with database() as client:
            for kind in ("pdf", "csv"):
                name = collection_name(kind)
                result["collections"][kind] = client.count(name, exact=True).count if client.collection_exists(name) else None
    except Exception as error:
        result["problems"].append(str(error) or type(error).__name__)
    return result


def answer_from_base(question: str, csv_corpus: bool, top_k: int | None) -> dict:
    if not question:
        raise ValueError("Введите вопрос")
    if top_k is not None and top_k < 1:
        raise ValueError("--top-k должен быть положительным числом")
    from generation import csv_search_phrases, generate, search_terms
    from retrieval import search, search_csv

    if csv_corpus:
        phrases = csv_search_phrases(question)
        results = search_csv(question, phrases, top_k)
    else:
        keywords = search_terms(question)
        phrases = [line.strip() for line in keywords.splitlines() if line.strip()]
        results = search(question, top_k, terms=keywords)
    sources = [
        {
            "number": number,
            "reference": fragment.reference,
            "publication_id": next(iter(publication_ids(fragment.source)), None),
            "page": fragment.page,
            "text": fragment.text,
        }
        for number, fragment in enumerate(results, start=1)
    ]
    return {"answer": generate(question, results, csv_corpus=csv_corpus), "phrases": phrases, "sources": sources}


def answer_from_web(question: str, phrases: list[str]) -> dict:
    if not question:
        raise ValueError("Введите вопрос")
    from generation import generate_web
    from web_search import find_web_passages

    if not phrases:
        # без фраз из ответа по базе ищем по самому вопросу: лишний вызов модели только удлиняет ожидание
        phrases = [question]
    answer, cited = generate_web(question, find_web_passages(question, phrases))
    sources = [
        {"number": number, "title": passage.title, "url": passage.url, "fetched_at": passage.fetched_at, "text": passage.text}
        for number, passage in cited.items()
    ]
    return {"answer": answer, "sources": sources}


def describe(error: BaseException) -> str:
    message = str(error) or type(error).__name__
    cause = error.__cause__ or error.__context__
    if cause is not None and str(cause) and str(cause) not in message:
        message += f" ({type(cause).__name__}: {cause})"
    return message


def emit(payload: dict) -> None:
    sys.stdout.buffer.write(json.dumps(payload).encode("ascii") + b"\n")
    sys.stdout.flush()


if __name__ == "__main__":
    main()
