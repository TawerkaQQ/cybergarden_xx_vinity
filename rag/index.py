import argparse
import csv
from pathlib import Path

from config import DATA_DIR
from csv_corpus import load_csv_corpus
from ingestion import load_pdfs_from_data_dir
from vector_store import replace_index


def main():
    parser = argparse.ArgumentParser(description="Индексация PDF или CSV в Qdrant")
    parser.add_argument("--csv", action="store_true", help="загрузить таблицы из data/rag/dev_gold")
    parser.add_argument("--data", type=Path, help="папка с входными файлами")
    args = parser.parse_args()
    try:
        if args.csv:
            count = replace_index("csv", load_csv_corpus(args.data or DATA_DIR / "dev_gold"))
        else:
            count = replace_index("pdf", load_pdfs_from_data_dir(args.data or DATA_DIR / "dev"))
        print(f"Записано фрагментов в Qdrant {count}")
    except (OSError, ValueError, csv.Error) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
