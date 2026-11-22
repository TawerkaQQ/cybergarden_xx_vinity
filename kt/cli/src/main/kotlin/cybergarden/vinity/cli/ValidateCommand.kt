package cybergarden.vinity.cli

import cybergarden.vinity.data.csv.CsvSubmissionRepository
import cybergarden.vinity.data.pages.JsonPageTextSource
import cybergarden.vinity.data.text.QuoteNormalizer
import cybergarden.vinity.domain.model.Profile
import cybergarden.vinity.domain.model.Severity
import cybergarden.vinity.domain.usecase.validation.ValidateSubmission
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension

class ValidateCommand(private val command: Command.Validate, private val profile: Profile) {

    fun run(): Int {
        val loaded = CsvSubmissionRepository().load(command.submission, profile)
        val pages = command.pages?.let { JsonPageTextSource(Path.of(it)) }
        val articles = command.articles?.let(::articleIds)
        val violations = loaded.violations + ValidateSubmission.of(pages, QuoteNormalizer, articles)(loaded.submission)

        violations
            .sortedWith(compareBy({ it.severity }, { it.table }, { it.publicationId }, { it.recordId }))
            .forEach { v ->
                val label = if (v.severity == Severity.ERROR) "ОШИБКА" else "внимание"
                val where = listOfNotNull(v.table, v.publicationId, v.recordId).joinToString(" ")
                println("$label  $where: ${v.message}")
            }

        val errors = violations.count { it.severity == Severity.ERROR }
        val warnings = violations.size - errors
        println("\nПрофиль: ${profile.name}. Ошибок: $errors, предупреждений: $warnings")
        return if (errors == 0) 0 else 1
    }

    private fun articleIds(directory: String): Set<String> =
        Path.of(directory).listDirectoryEntries().filter { it.extension.equals("pdf", ignoreCase = true) }
            .map { it.nameWithoutExtension }.toSet()
}
