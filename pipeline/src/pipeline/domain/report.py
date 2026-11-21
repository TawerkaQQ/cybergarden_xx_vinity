from typing import Any

from pipeline.domain.highlight import Highlight
from pipeline.domain.profile import PUBLICATION_ID, Profile


def article_report(
    profile: Profile,
    publication_id: str,
    prompt_version: str,
    tables: dict[str, list[dict[str, str]]],
    highlights: dict[str, Highlight],
    page_sizes: dict[int, tuple[float, float]],
) -> dict[str, Any]:
    return {
        "publication_id": publication_id,
        "profile": profile.name,
        "prompt_version": prompt_version,
        "pages": {str(number): {"width": width, "height": height} for number, (width, height) in page_sizes.items()},
        "tables": {
            name: [row for row in rows if row[PUBLICATION_ID] == publication_id] for name, rows in tables.items()
        },
        "highlights": {result_id: highlight.to_json() for result_id, highlight in highlights.items()},
    }
