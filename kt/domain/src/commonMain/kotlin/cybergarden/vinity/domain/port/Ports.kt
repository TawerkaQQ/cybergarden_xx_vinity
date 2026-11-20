package cybergarden.vinity.domain.port

import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.Violation

data class LoadedSubmission(
    val submission: Submission,
    val violations: List<Violation>,
)

interface SubmissionRepository {
    fun load(location: String): LoadedSubmission
    fun save(location: String, submission: Submission)
}

data class PageText(val page: Int, val text: String)

interface PageTextSource {
    fun pages(publicationId: String): List<PageText>?
}

interface PageRenderer {
    fun render(document: String, page: Int, highlight: String?): ByteArray?
}
