import argparse
from pathlib import Path

from pipeline.application.build_pages import BuildPages
from pipeline.application.folder import article_pdfs
from pipeline.infrastructure.pages_store import JsonPagesStore
from pipeline.infrastructure.pypdf_reader import PypdfArticleReader


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(prog="python -m pipeline")
    commands = parser.add_subparsers(dest="command", required=True)
    pages = commands.add_parser("pages", help="текст страниц PDF через pypdf")
    pages.add_argument("folders", nargs="+", type=Path)
    pages.add_argument("--out", type=Path, default=Path("work"))
    pages.add_argument("--only", nargs="*")
    return parser.parse_args()


def run_pages(args: argparse.Namespace) -> None:
    build = BuildPages(PypdfArticleReader(), JsonPagesStore(args.out / "pages"))
    only = set(args.only) if args.only else None
    for folder in args.folders:
        for pdf in article_pdfs(folder, only):
            print(f"{pdf.stem}: {build(pdf)} стр.")


def main() -> None:
    args = parse_args()
    if args.command == "pages":
        run_pages(args)


if __name__ == "__main__":
    main()
