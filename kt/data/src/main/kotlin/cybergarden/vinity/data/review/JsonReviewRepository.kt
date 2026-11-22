package cybergarden.vinity.data.review

import cybergarden.vinity.data.Workspace
import cybergarden.vinity.domain.model.IssueReview
import cybergarden.vinity.domain.model.IssueVerdict
import cybergarden.vinity.domain.model.RecordKey
import cybergarden.vinity.domain.model.ReviewScope
import cybergarden.vinity.domain.model.Reviews
import cybergarden.vinity.domain.model.ValueReview
import cybergarden.vinity.domain.model.ValueVerdict
import cybergarden.vinity.domain.port.ReviewRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.readText
import kotlin.io.path.writeText

class JsonReviewRepository(private val workspace: Workspace) : ReviewRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val files = ConcurrentHashMap<Path, Pair<Long, Map<String, Reviews>>>()

    override fun load(scope: ReviewScope): Reviews = read(file(scope))[scope.source] ?: EMPTY

    @Synchronized
    override fun update(scope: ReviewScope, change: (Reviews) -> Reviews): Reviews {
        val path = file(scope)
        val sections = read(path).toMutableMap()
        val updated = change(sections[scope.source] ?: EMPTY)
        if (updated.values.isEmpty() && updated.issues.isEmpty()) sections.remove(scope.source) else sections[scope.source] = updated
        if (sections.isEmpty()) {
            path.deleteIfExists()
        } else {
            path.parent.createDirectories()
            path.writeText(json.encodeToString(FileDocument.serializer(), sections.toDocument()), Charsets.UTF_8)
        }
        files.remove(path)
        return updated
    }

    private fun file(scope: ReviewScope): Path = workspace.reviews(scope.profile, scope.set)

    private fun read(path: Path): Map<String, Reviews> {
        if (!path.exists()) return emptyMap()
        val modified = path.getLastModifiedTime().toMillis()
        files[path]?.takeIf { it.first == modified }?.let { return it.second }
        val sections = json.decodeFromString(FileDocument.serializer(), path.readText(Charsets.UTF_8)).toSections()
        files[path] = modified to sections
        return sections
    }

    private fun FileDocument.toSections(): Map<String, Reviews> = sections.mapValues { (_, section) ->
        Reviews(
            values = section.values.mapNotNull { (key, review) ->
                key.toRecordKey()?.let { it to ValueReview(ValueVerdict.valueOf(review.state), review.value) }
            }.toMap(),
            issues = section.issues.mapNotNull { (key, review) ->
                key.toRecordKey()?.let { it to IssueReview(IssueVerdict.valueOf(review.state), review.reason) }
            }.toMap(),
        )
    }

    private fun Map<String, Reviews>.toDocument() = FileDocument(
        mapValues { (_, reviews) ->
            SectionDocument(
                values = reviews.values.entries.associate { (key, review) -> key.text() to ValueDocument(review.verdict.name, review.value) },
                issues = reviews.issues.entries.associate { (key, review) -> key.text() to IssueDocument(review.verdict.name, review.reason) },
            )
        },
    )

    private fun RecordKey.text() = "$publicationId$SEPARATOR$id"

    private fun String.toRecordKey(): RecordKey? {
        val publication = substringBefore(SEPARATOR, "")
        val id = substringAfter(SEPARATOR, "")
        return if (publication.isEmpty() || id.isEmpty()) null else RecordKey(publication, id)
    }

    @Serializable
    private data class FileDocument(val sections: Map<String, SectionDocument> = emptyMap())

    @Serializable
    private data class SectionDocument(
        val values: Map<String, ValueDocument> = emptyMap(),
        val issues: Map<String, IssueDocument> = emptyMap(),
    )

    @Serializable
    private data class ValueDocument(val state: String, val value: String? = null)

    @Serializable
    private data class IssueDocument(val state: String, val reason: String? = null)

    private companion object {
        const val SEPARATOR = "/"
        val EMPTY = Reviews()
    }
}
