package cybergarden.vinity.data.csv

import cybergarden.vinity.domain.model.Profile
import cybergarden.vinity.domain.model.Publications
import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.TableSpec
import cybergarden.vinity.domain.model.Violation
import cybergarden.vinity.domain.port.LoadedSubmission
import cybergarden.vinity.domain.port.SubmissionRepository
import java.nio.file.Path
import kotlin.io.path.exists

class CsvSubmissionRepository : SubmissionRepository {

    override fun load(location: String, profile: Profile): LoadedSubmission {
        val directory = Path.of(location)
        val violations = mutableListOf<Violation>()
        val tables = profile.allTables.associate { spec -> spec.name to read(directory, spec, violations) }
        return LoadedSubmission(Submission(profile, tables), violations)
    }

    private fun read(directory: Path, spec: TableSpec, violations: MutableList<Violation>): List<Record> {
        val path = directory.resolve(spec.file)
        if (!path.exists()) {
            violations += Violation.error(spec.name, null, null, "нет файла ${spec.file}")
            return emptyList()
        }
        val content = CsvFiles.read(path)
        violations += headerProblems(spec, content.header).map { Violation.error(spec.name, null, null, it) }
        return content.rows.map { row ->
            val record = Record(row.cells[Publications.ID].orEmpty(), row.cells[spec.id].orEmpty(), row.cells)
            if (!row.complete) violations += Violation.error(spec.name, record, "строка ${row.number}: неверное число ячеек")
            record
        }
    }

    private fun headerProblems(spec: TableSpec, header: List<String>): List<String> {
        val missing = spec.columns - header.toSet()
        val extra = header - spec.columns.toSet()
        return missing.map { "нет колонки $it" } +
            extra.map { "лишняя колонка $it" } +
            listOfNotNull("порядок колонок не как в профиле".takeIf { missing.isEmpty() && extra.isEmpty() && header != spec.columns })
    }
}
