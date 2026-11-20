package cybergarden.vinity.server

import cybergarden.vinity.api.DatasetDto
import cybergarden.vinity.api.DatasetRef
import cybergarden.vinity.api.ScoreDto
import cybergarden.vinity.api.toDatasetDto
import cybergarden.vinity.api.toDto
import cybergarden.vinity.data.Workspace
import cybergarden.vinity.domain.model.MatchMode
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.port.PageRenderer
import cybergarden.vinity.domain.port.SubmissionRepository
import cybergarden.vinity.domain.usecase.ScoreSubmission
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class DatasetService(
    private val workspace: Workspace,
    private val repository: SubmissionRepository,
    private val renderer: PageRenderer,
) {
    private val score = ScoreSubmission()

    fun datasets(): List<DatasetRef> = workspace.profiles.names().flatMap { profile ->
        val publications = workspace.profiles.load(profile).publications.file
        workspace.output().resolve(profile).subdirectories()
            .filter { it.resolve(publications).exists() }
            .map { DatasetRef(profile, it.name) }
    }

    fun dataset(ref: DatasetRef): DatasetDto? = submission(ref)?.toDatasetDto(ref)

    fun score(ref: DatasetRef, strict: Boolean): ScoreDto? {
        val candidate = submission(ref) ?: return null
        val gold = workspace.gold(ref.profile, ref.set).takeIf { it.isDirectory() } ?: return null
        val mode = if (strict) MatchMode.STRICT else MatchMode.LOOSE
        return score(candidate, repository.load(gold.toString(), candidate.profile).submission, mode).toDto()
    }

    fun page(ref: DatasetRef, publicationId: String, page: Int, fragment: String?): ByteArray? {
        if (ref !in datasets() || !SAFE_ID.matches(publicationId)) return null
        val pdf = workspace.articles(ref.profile, ref.set).resolve("$publicationId.pdf")
        return renderer.render(pdf.toString(), page, fragment)
    }

    fun webDistribution(): Path? =
        workspace.root.resolve("kt/app/build/dist/wasmJs/productionExecutable").takeIf { it.isDirectory() }

    private fun submission(ref: DatasetRef): Submission? {
        if (ref !in datasets()) return null
        val profile = workspace.profiles.load(ref.profile)
        return repository.load(workspace.output(ref.profile, ref.set).toString(), profile).submission
    }

    private fun Path.subdirectories(): List<Path> =
        if (isDirectory()) listDirectoryEntries().filter { it.isDirectory() }.sortedBy { it.name } else emptyList()

    private companion object {
        val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]*")
    }
}
