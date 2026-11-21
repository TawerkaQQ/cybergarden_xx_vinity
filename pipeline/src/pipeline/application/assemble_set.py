from pathlib import Path

from pipeline.domain.assembly import AssembledSet, Assembler
from pipeline.domain.ports import TableStore, WorkSource


class AssembleSet:
    def __init__(self, assembler: Assembler, work: WorkSource, tables: TableStore):
        self._assembler = assembler
        self._work = work
        self._tables = tables

    def __call__(self, publication_ids: list[str], directory: Path) -> AssembledSet:
        assembled = self._assembler.assemble(
            publication_ids, self._work.extracts(publication_ids), self._work.pages(publication_ids)
        )
        self._tables.write(directory, assembled.tables)
        return assembled
