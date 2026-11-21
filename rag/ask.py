from retrieval import search
from generation import generate

def main():
    query = input("Введите вопрос: ").strip()
    if not query:
        return

    results = search(query)

    print("\n--- Найденные фрагменты ---")
    for i, r in enumerate(results, 1):
        source = r.metadata.get("source", "unknown")
        page = r.metadata.get("page", "?")
        print(f"[{i}] ({source}, стр. {page}) {r.page_content[:200]}...")

    print("\n--- Ответ ---")
    print(generate(query, results))

if __name__ == "__main__":
    main()