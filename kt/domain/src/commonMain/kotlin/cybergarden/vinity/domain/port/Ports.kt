package cybergarden.vinity.domain.port

import cybergarden.vinity.domain.model.Profile
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.Violation

interface ProfileRepository {
    fun load(name: String): Profile
}

data class LoadedSubmission(
    val submission: Submission,
    val violations: List<Violation>,
)

interface SubmissionRepository {
    fun load(location: String, profile: Profile): LoadedSubmission
}

data class PageText(val page: Int, val variants: Map<String, String>)

interface PageTextSource {
    fun pages(publicationId: String): List<PageText>?
}

fun interface TextNormalizer {
    fun normalize(text: String): String
}

interface PageRenderer {
    fun render(document: String, page: Int, highlight: String?): ByteArray?
}
