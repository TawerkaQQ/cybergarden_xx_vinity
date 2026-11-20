package cybergarden.vinity.data.csv

import cybergarden.vinity.domain.model.Severity
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.Violation
import cybergarden.vinity.domain.port.LoadedSubmission
import cybergarden.vinity.domain.port.SubmissionRepository
import java.nio.file.Path
import kotlin.io.path.exists

class CsvSubmissionRepository : SubmissionRepository {

    override fun load(location: String): LoadedSubmission {
        val directory = Path.of(location)
        val violations = mutableListOf<Violation>()

        fun <T> table(table: SubmissionTable, map: RowReader.() -> T): List<T> {
            val path = directory.resolve(table.fileName)
            if (!path.exists()) {
                violations += Violation(Severity.ERROR, table.tableName, null, null, "нет файла ${table.fileName}")
                return emptyList()
            }
            val content = CsvFiles.read(path)
            (table.columns - content.header.toSet()).forEach {
                violations += Violation(Severity.ERROR, table.tableName, null, null, "нет колонки $it")
            }
            (content.header - table.columns.toSet()).forEach {
                violations += Violation(Severity.WARNING, table.tableName, null, null, "лишняя колонка $it")
            }
            return content.rows.map { RowReader(table, it, violations).map() }
        }

        val submission = Submission(
            publications = table(SubmissionTable.PUBLICATIONS) { toPublication() },
            materials = table(SubmissionTable.MATERIALS) { toMaterial() },
            devices = table(SubmissionTable.DEVICES) { toDevice() },
            layers = table(SubmissionTable.LAYERS) { toLayer() },
            measurements = table(SubmissionTable.MEASUREMENTS) { toMeasurement() },
            results = table(SubmissionTable.RESULTS) { toMetricResult() },
            issues = table(SubmissionTable.ISSUES) { toIssue() },
        )
        return LoadedSubmission(submission, violations)
    }

    override fun save(location: String, submission: Submission) {
        val directory = Path.of(location)
        fun write(table: SubmissionTable, rows: List<List<String>>) =
            CsvFiles.write(directory.resolve(table.fileName), table.columns, rows)

        write(SubmissionTable.PUBLICATIONS, submission.publications.map { it.toRow() })
        write(SubmissionTable.MATERIALS, submission.materials.map { it.toRow() })
        write(SubmissionTable.DEVICES, submission.devices.map { it.toRow() })
        write(SubmissionTable.LAYERS, submission.layers.map { it.toRow() })
        write(SubmissionTable.MEASUREMENTS, submission.measurements.map { it.toRow() })
        write(SubmissionTable.RESULTS, submission.results.map { it.toRow() })
        write(SubmissionTable.ISSUES, submission.issues.map { it.toRow() })
    }
}
