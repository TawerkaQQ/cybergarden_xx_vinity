import re
from collections import Counter
from typing import Any

from pipeline.domain.extraction_shape import as_text
from pipeline.domain.numbers import parse_number


def majority(runs: list[list[dict[str, Any]]]) -> list[dict[str, Any]]:
    if len(runs) == 1:
        return runs[0]
    quorum = len(runs) // 2 + 1
    counts = [Counter(vote_key(mention) for mention in run) for run in runs]
    needed = {
        key: sorted((count[key] for count in counts), reverse=True)[quorum - 1] for key in set().union(*counts)
    }
    kept, used = [], Counter()
    for run in runs:
        for mention in run:
            key = vote_key(mention)
            if used[key] < needed[key]:
                kept.append(mention)
                used[key] += 1
    return kept


def vote_key(mention: dict[str, Any]) -> tuple[str, float | None, str, bool]:
    metric = re.sub(r"\s+", " ", str(mention["metric"])).strip().casefold()
    return metric, parse_number(as_text(mention["raw_value"])), str(mention["origin"]), bool(mention["table"])
