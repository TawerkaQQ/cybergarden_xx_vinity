import re
import sqlite3
from contextlib import closing
from pathlib import Path

import config
from evidence import Fragment

PUBLICATION_ID = re.compile(r"\b[DP]\d{3}\b", re.IGNORECASE)
WORD = re.compile(r"[^\W_]+")


def search(query: str, k: int | None = None) -> list[Fragment]:
    terms = dict.fromkeys(WORD.findall(query.casefold())[:64])
    if not terms:
        return []

    limit = config.get_top_k() if k is None else k
    expression = " OR ".join(f'"{term}"' for term in terms)
    matches = read_matches(config.INDEX_PATH, expression, limit, weighted=False)
    approved = search_approved(expression, limit)
    if not approved:
        return matches
    return rank_matches(matches + approved, set(terms), limit)


def search_csv(query: str, phrases: list[str], k: int | None = None) -> list[Fragment]:
    identifiers = dict.fromkeys(PUBLICATION_ID.findall(query.casefold()))
    words = WORD.findall(PUBLICATION_ID.sub("", query).casefold())[:32]
    terms = dict.fromkeys(
        [" ".join(WORD.findall(phrase.casefold())) for phrase in phrases] + words
    )
    content = " OR ".join(f'"{term}"' for term in terms if term and term not in identifiers)
    scope = " OR ".join(f'keywords:"{identifier}"' for identifier in identifiers)
    expression = f"({scope}) AND ({content})" if scope and content else scope or content
    if not expression:
        return []

    limit = config.get_top_k(8) if k is None else k
    if limit < 1:
        raise ValueError("TOP_K должен быть положительным числом")
    matches = read_matches(config.CSV_INDEX_PATH, expression, max(32, limit * 4), weighted=True)
    matches.extend(search_approved(expression, limit))
    return rank_matches(matches, set(words), limit)


def search_approved(expression: str, limit: int) -> list[Fragment]:
    path = config.APPROVED_WEB_PATH
    if not path.is_file():
        return []
    try:
        return read_matches(path, expression, limit, weighted=True)
    except sqlite3.OperationalError as error:
        if str(error) != "no such table: fragments":
            raise
        return []


def rank_matches(matches: list[Fragment], literal_words: set[str], limit: int) -> list[Fragment]:
    matches.sort(
        key=lambda fragment: len(literal_words.intersection(WORD.findall((fragment.keywords or fragment.text).casefold()))),
        reverse=True,
    )
    return matches[:limit]


def read_matches(path: Path, expression: str, limit: int, *, weighted: bool) -> list[Fragment]:
    if limit < 1:
        raise ValueError("TOP_K должен быть положительным числом")
    with closing(sqlite3.connect(path.as_uri() + "?mode=ro", uri=True)) as database:
        rank = "bm25(fragments, 0, 0, 1, 4)" if weighted else "rank"
        fields = "source, page, text, keywords" if weighted else "source, page, text"
        rows = database.execute(
            f"SELECT {fields} FROM fragments WHERE fragments MATCH ? ORDER BY {rank}, rowid LIMIT ?",
            (expression, limit),
        )
        return [Fragment(*row) for row in rows]
