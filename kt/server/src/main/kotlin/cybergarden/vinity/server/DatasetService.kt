package cybergarden.vinity.server

import cybergarden.vinity.api.DatasetDto
import cybergarden.vinity.api.ScoreDto
import cybergarden.vinity.api.toDatasetDto
import cybergarden.vinity.api.toDto
import cybergarden.vinity.domain.port.PageRenderer
import cybergarden.vinity.domain.port.SubmissionRepository
import cybergarden.vinity.domain.usecase.MatchMode
import cybergarden.vinity.domain.usecase.ScoreSubmission
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class DatasetService(
    private val root: Path,
    private val repository: SubmissionRepository,
    private val renderer: PageRenderer,
) {
    private val score = ScoreSubmission()

    fun datasets(): List<String> {
        val output = root.resolve("out")
        if (!output.isDirectory()) return emptyList()
        return output.listDirectoryEntries()
            .filter { it.isDirectory() && it.resolve("publications.csv").exists() }
            .map { it.name }
            .sorted()
    }

    fun dataset(name: String): DatasetDto? =
        submissionDirectory(name)?.let { repository.load(it.toString()).submission.toDatasetDto(name) }

    fun score(name: String, strict: Boolean): ScoreDto? {
        val candidate = submissionDirectory(name) ?: return null
        val gold = root.resolve("data").resolve("${name}_gold").takeIf { it.isDirectory() } ?: return null
        val mode = if (strict) MatchMode.STRICT else MatchMode.LOOSE
        return score(
            repository.load(candidate.toString()).submission,
            repository.load(gold.toString()).submission,
            mode,
        ).toDto()
    }

    fun page(name: String, publicationId: String, page: Int, fragment: String?): ByteArray? {
        if (name !in datasets() || !SAFE_ID.matches(publicationId)) return null
        val pdf = root.resolve("data").resolve(name).resolve("$publicationId.pdf")
        return renderer.render(pdf.toString(), page, fragment)
    }

    fun webDistribution(): Path? =
        root.resolve("kt/app/build/dist/wasmJs/productionExecutable").takeIf { it.isDirectory() }

    private fun submissionDirectory(name: String): Path? =
        if (name in datasets()) root.resolve("out").resolve(name) else null

    private companion object {
        val SAFE_ID = Regex("[A-Za-z0-9_-]+")
    }
}
