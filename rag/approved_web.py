import hashlib
import sqlite3
from contextlib import closing
from datetime import UTC, datetime

import config
from evidence import WebPassage


def save_passages(question: str, passages: list[WebPassage]) -> int:
    if not passages:
        return 0

    approved_at = datetime.now(UTC).isoformat(timespec="seconds")
    inserted = 0
    with closing(sqlite3.connect(config.APPROVED_WEB_PATH)) as database, database:
        database.execute("BEGIN IMMEDIATE")
        database.execute(
            """CREATE TABLE IF NOT EXISTS approved_sources (
                id INTEGER PRIMARY KEY,
                signature TEXT NOT NULL UNIQUE,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                passage TEXT NOT NULL,
                fetched_at TEXT NOT NULL,
                approved_at TEXT NOT NULL
            )"""
        )
        database.execute(
            "CREATE VIRTUAL TABLE IF NOT EXISTS fragments USING fts5(source UNINDEXED, page UNINDEXED, text, keywords)"
        )
        for passage in passages:
            signature = hashlib.sha256(f"{passage.url}\n{passage.text}".encode()).hexdigest()
            row = database.execute(
                """INSERT INTO approved_sources (signature, url, title, passage, fetched_at, approved_at)
                   VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(signature) DO NOTHING RETURNING id""",
                (signature, passage.url, passage.title, passage.text, passage.fetched_at, approved_at),
            ).fetchone()
            if row is None:
                continue
            database.execute(
                "INSERT INTO fragments (rowid, source, page, text, keywords) VALUES (?, ?, NULL, ?, ?)",
                (row[0], f"Одобренный веб-источник. {passage.reference}", passage.text, f"{passage.title} {question}"),
            )
            inserted += 1
    return inserted
