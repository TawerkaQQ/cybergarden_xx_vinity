import json
from pathlib import Path

from pipeline.domain.profile import Profile


def load_profile(profiles: Path, name: str) -> Profile:
    directory = profiles / name
    payload = json.loads((directory / "profile.json").read_text(encoding="utf-8"))
    return Profile.from_json(payload, (directory / "rules.md").read_text(encoding="utf-8"))
