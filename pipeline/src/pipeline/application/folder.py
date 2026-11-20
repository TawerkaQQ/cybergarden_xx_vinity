from pathlib import Path


def article_pdfs(folder: Path, only: set[str] | None = None) -> list[Path]:
    pdfs = sorted(folder.glob("*.pdf"))
    return [pdf for pdf in pdfs if not only or pdf.stem in only]
