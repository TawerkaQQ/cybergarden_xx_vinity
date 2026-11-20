import json
import re
from pathlib import Path
from typing import Any

LONE_SURROGATE = re.compile(r"[\ud800-\udfff]")


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    escaped = LONE_SURROGATE.sub(lambda match: f"\\u{ord(match.group()):04x}", text)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(escaped, encoding="utf-8")
    temporary.replace(path)


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))
