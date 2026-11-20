package cybergarden.vinity.cli

import cybergarden.vinity.data.csv.CsvSubmissionRepository
import cybergarden.vinity.data.pages.JsonPageTextSource
import cybergarden.vinity.domain.model.Severity
import cybergarden.vinity.domain.usecase.ValidateSubmission
import java.nio.file.Path

class ValidateCommand(private val command: Command.Validate) {

    fun run(): Int {
        val loaded = CsvSubmissionRepository().load(command.submission)
        val pages = command.pages?.let { JsonPageTextSource(Path.of(it)) }
        val violations = loaded.violations + ValidateSubmission(pages)(loaded.submission)

        violations
            .sortedWith(compareBy({ it.severity }, { it.table }, { it.publicationId }, { it.recordId }))
            .forEach { v ->
                val label = if (v.severity == Severity.ERROR) "ОШИБКА" else "внимание"
                val where = listOfNotNull(v.table, v.publicationId, v.recordId).joinToString(" ")
                println("$label  $where: ${v.message}")
            }

        val errors = violations.count { it.severity == Severity.ERROR }
        val warnings = violations.size - errors
        println("\nОшибок: $errors, предупреждений: $warnings")
        return if (errors == 0) 0 else 1
    }
}
