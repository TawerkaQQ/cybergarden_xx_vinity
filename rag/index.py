import argparse
import csv
import sqlite3
from pathlib import Path

from config import CSV_INDEX_PATH, DATA_DIR
from csv_corpus import load_csv_corpus
from ingestion import ingest, load_pdfs_from_data_dir


def main():
    parser = argparse.ArgumentParser(description="Локальная индексация PDF или CSV")
    parser.add_argument("--csv", action="store_true", help="загрузить таблицы из data/rag/dev_gold")
    parser.add_argument("--data", type=Path, help="папка с входными файлами")
    args = parser.parse_args()
    try:
        if args.csv:
            ingest(load_csv_corpus(args.data or DATA_DIR / "dev_gold"), CSV_INDEX_PATH)
        else:
            ingest(load_pdfs_from_data_dir(args.data or DATA_DIR / "dev"))
    except (OSError, ValueError, csv.Error, sqlite3.Error) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
