import argparse
import sys

from approved_web import save_passages
from generation import csv_search_phrases, generate, generate_web, search_terms
from retrieval import search, search_csv
from web_search import find_web_passages


def main():
    parser = argparse.ArgumentParser(description="Вопрос к проиндексированным PDF или CSV")
    parser.add_argument("--csv", action="store_true", help="искать по таблицам из data/rag/dev_gold")
    parser.add_argument("--top-k", type=int, help="сколько записей передать модели")
    parser.add_argument("--local-only", action="store_true", help="показать только ответ из локальной базы")
    parser.add_argument("question", nargs="?")
    args = parser.parse_args()
    query = (args.question if args.question is not None else input("Введите вопрос: ")).strip()
    if not query:
        parser.error("Введите вопрос")

    try:
        if args.top_k is not None and args.top_k < 1:
            raise ValueError("--top-k должен быть положительным числом")
        if args.csv:
            phrases = csv_search_phrases(query)
            results = search_csv(query, phrases, args.top_k)
        else:
            keywords = search_terms(query)
            phrases = keywords.splitlines()
            results = search(query, args.top_k, terms=keywords)

        print("1. Ответ из локальной базы")
        for number, result in enumerate(results, start=1):
            snippet = f" — {result.text[:200]}" if not args.csv else ""
            print(f"[{number}] {result.reference}{snippet}")
        print(generate(query, results, csv_corpus=args.csv), flush=True)
        if not args.local_only:
            show_web_answer(query, phrases)
    except (OSError, ValueError) as error:
        parser.exit(1, f"{error}\n")


def show_web_answer(query: str, phrases: list[str]) -> None:
    print("\n2. Дополнительный ответ из интернета", flush=True)
    try:
        answer, sources = generate_web(query, find_web_passages(query, phrases))
    except (OSError, ValueError) as error:
        print(f"Веб-ответ недоступен. {error}")
        return
    print(answer)
    for number, passage in sources.items():
        print(f"[{number}] {passage.reference}")
    if not sources or not sys.stdin.isatty():
        return

    try:
        choice = input("\n1 — оставить ответ из базы, 2 — добавить источники веб-ответа в базу. Выбор [1] ").strip()
    except (EOFError, KeyboardInterrupt):
        print()
        return
    if choice == "2":
        added = save_passages(query, list(sources.values()))
        print(f"Добавлено новых фрагментов {added}.")


if __name__ == "__main__":
    main()
