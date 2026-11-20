from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path

from pipeline.domain.extract import ExtractResult


@dataclass(frozen=True)
class Outcome:
    pdf: Path
    result: ExtractResult | None
    error: Exception | None


class ProcessFolder:
    def __init__(self, extract: Callable[[Path], ExtractResult], workers: int):
        self._extract = extract
        self._workers = workers

    def __call__(self, pdfs: list[Path], report: Callable[[Outcome], None]) -> list[Outcome]:
        with ThreadPoolExecutor(max_workers=self._workers) as pool:
            futures = {pool.submit(self._extract, pdf): pdf for pdf in pdfs}
            outcomes = []
            for future in as_completed(futures):
                outcome = self._outcome(futures[future], future)
                report(outcome)
                outcomes.append(outcome)
        return sorted(outcomes, key=lambda outcome: outcome.pdf.name)

    @staticmethod
    def _outcome(pdf: Path, future) -> Outcome:
        try:
            return Outcome(pdf, future.result(), None)
        except Exception as error:
            return Outcome(pdf, None, error)
