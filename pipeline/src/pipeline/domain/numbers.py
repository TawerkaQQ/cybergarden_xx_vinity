import re
import unicodedata

DASHES = str.maketrans({"−": "-", "–": "-", "—": "-", "‒": "-", " ": " ", " ": " "})
SCIENTIFIC = re.compile(r"(-?\d+(?:[.,]\d+)?)\s*[×xX*·]\s*10\s*\^?\s*(\(?-?\d+\)?)?")
THOUSANDS = re.compile(r"-?[1-9]\d{0,2}(?:,\d{3})+(?:\.\d+)?(?!\d)")
PLAIN = re.compile(r"-?\d+(?:[.,]\d+)?|-?[.,]\d+")


def parse_number(text: str | None) -> float | None:
    if not text:
        return None
    cleaned = unicodedata.normalize("NFKC", str(text)).translate(DASHES).split("±")[0]
    scientific = SCIENTIFIC.search(cleaned)
    if scientific:
        if not scientific.group(2):
            return None
        return decimal(scientific.group(1)) * 10 ** int(scientific.group(2).strip("()"))
    thousands = THOUSANDS.search(cleaned)
    if thousands:
        return float(thousands.group().replace(",", ""))
    plain = PLAIN.search(cleaned)
    return decimal(plain.group()) if plain else None


def decimal(token: str) -> float:
    return float(token.replace(",", "."))


def format_number(value: float | None) -> str:
    if value is None:
        return ""
    rounded = round(value, 10)
    if rounded == int(rounded) and abs(rounded) < 1e15:
        return str(int(rounded))
    return f"{rounded:.10g}"
