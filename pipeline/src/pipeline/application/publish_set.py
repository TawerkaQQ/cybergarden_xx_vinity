from pathlib import Path

from pipeline.domain.assembly import AssembledSet, Assembler, highlight_text
from pipeline.domain.checks import Checker
from pipeline.domain.highlight import Highlight
from pipeline.domain.numbers import parse_number
from pipeline.domain.ports import PageGeometry, ReportStore, TableStore, WorkSource
from pipeline.domain.profile import PUBLICATION_ID, Profile
from pipeline.domain.report import article_report


class PublishSet:
    def __init__(
        self,
        profile: Profile,
        assembler: Assembler,
        checker: Checker,
        work: WorkSource,
        tables: TableStore,
        geometry: PageGeometry,
        reports: ReportStore,
    ):
        self._profile = profile
        self._assembler = assembler
        self._checker = checker
        self._work = work
        self._tables = tables
        self._geometry = geometry
        self._reports = reports

    def __call__(self, publication_ids: list[str], directory: Path) -> AssembledSet:
        extracts = self._work.extracts(publication_ids)
        assembled = self._assembler.assemble(publication_ids, extracts, self._work.pages(publication_ids))
        issues, checked = self._checker.check(assembled.tables)
        assembled.report.update({f"проверка {name}": count for name, count in checked.items()})
        self._tables.write(directory, assembled.tables, issues)
        values = assembled.tables[self._profile.values.table]
        for publication_id in publication_ids:
            highlights = self._highlights(publication_id, [row for row in values if row[PUBLICATION_ID] == publication_id])
            assembled.report["подсветка: найдена"] += len(highlights)
            report = article_report(
                self._profile,
                publication_id,
                extracts.get(publication_id, {}).get("prompt_version", ""),
                assembled.tables,
                highlights,
                self._geometry.page_sizes(publication_id),
                issues,
            )
            self._reports.write(directory, report)
        return assembled

    def _highlights(self, publication_id: str, rows: list[dict[str, str]]) -> dict[str, Highlight]:
        values = self._profile.values
        highlights = {}
        for row in rows:
            page = parse_number(row.get(values.column("page")))
            fragment = row.get(values.column("fragment"), "")
            if not page or not fragment:
                continue
            highlight = self._geometry.locate(publication_id, int(page), highlight_text(fragment), row.get(values.column("raw_value"), ""))
            if highlight:
                highlights[row[values.column("id")]] = highlight
        return highlights
