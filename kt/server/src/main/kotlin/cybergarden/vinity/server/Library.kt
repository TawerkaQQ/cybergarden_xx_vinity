package cybergarden.vinity.server

import cybergarden.vinity.api.SetRef
import cybergarden.vinity.api.SourceDto
import cybergarden.vinity.data.Workspace
import cybergarden.vinity.data.pages.FallbackPageTextSource
import cybergarden.vinity.data.pages.JsonPageTextSource
import cybergarden.vinity.data.pages.PdfPageTextSource
import cybergarden.vinity.data.pdf.PdfBoxDocumentInspector
import cybergarden.vinity.domain.model.Profile
import cybergarden.vinity.domain.model.RecordKey
import cybergarden.vinity.domain.model.ReviewScope
import cybergarden.vinity.domain.model.Reviews
import cybergarden.vinity.domain.port.ReviewRepository
import cybergarden.vinity.domain.port.SubmissionRepository
import cybergarden.vinity.domain.port.TextNormalizer
import cybergarden.vinity.domain.usecase.ArticleIndex
import cybergarden.vinity.domain.usecase.Audit
import cybergarden.vinity.domain.usecase.AuditValues
import cybergarden.vinity.domain.usecase.NaturalOrder
import cybergarden.vinity.domain.usecase.QuoteCheck
import cybergarden.vinity.domain.usecase.QuoteChecker
import cybergarden.vinity.domain.usecase.ValueCatalog
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

class Library(
    val workspace: Workspace,
    private val repository: SubmissionRepository,
    private val inspector: PdfBoxDocumentInspector,
    private val reviews: ReviewRepository,
    private val normalizer: TextNormalizer,
) {
    private class Loaded(val stamp: List<Long>, val index: ArticleIndex, val audit: Audit?, val checker: () -> QuoteChecker) {
        private val quotes = ConcurrentHashMap<String, Map<RecordKey, QuoteCheck>>()

        @Volatile
        var catalog: ValueCatalog? = null

        fun quotes(publicationId: String): Map<RecordKey, QuoteCheck> = quotes.computeIfAbsent(publicationId) {
            checker().checkAll(index.values(publicationId))
        }

        fun prefetch() {
            index.articleIds.parallelStream().forEach { quotes(it) }
        }

        fun variants(publicationId: String): List<String> = checker().variants(publicationId)
    }

    private val loaded = ConcurrentHashMap<Path, Loaded>()
    private val auditValues = AuditValues()

    fun profile(name: String): Profile = workspace.profiles.load(name)

    fun profileNames(): List<String> = workspace.profiles.names()

    fun sets(): List<SetRef> = profileNames().flatMap { profile -> workspace.sets(profile).map { SetRef(profile, it) } }

    fun uploads(ref: SetRef): Boolean = ref.set == Workspace.UPLOADS

    fun known(ref: SetRef): Boolean = ref.profile in profileNames() && ref.set in workspace.sets(ref.profile)

    fun known(ref: SetRef, id: String): Boolean =
        SAFE_ID.matches(id) && known(ref) && (pdf(ref, id).exists() || sources(ref).any { id in index(ref, it)?.articleIds.orEmpty() })

    fun sources(ref: SetRef): List<SourceDto> {
        val profile = profile(ref.profile)
        return SourceDto.entries.filter { hasTables(location(ref, it), profile) }
    }

    fun source(ref: SetRef, requested: SourceDto?): SourceDto? = requested?.takeIf { it in sources(ref) } ?: sources(ref).firstOrNull()

    fun canCompare(ref: SetRef): Boolean = sources(ref).containsAll(SourceDto.entries)

    fun index(ref: SetRef, source: SourceDto?): ArticleIndex? = source?.let { load(ref, it)?.index }

    fun audit(ref: SetRef, source: SourceDto?): Audit? = source?.let { load(ref, it)?.audit }

    fun catalog(ref: SetRef, source: SourceDto?, complete: Boolean = false): ValueCatalog? {
        val entry = source?.let { load(ref, it) } ?: return null
        if (complete) entry.prefetch()
        val current = reviews(ref, source)
        entry.catalog?.takeIf { it.reviews === current }?.let { return it }
        return ValueCatalog(entry.index, entry::quotes, current).also { entry.catalog = it }
    }

    fun variants(ref: SetRef, source: SourceDto?, id: String): List<String> = source?.let { load(ref, it)?.variants(id) }.orEmpty()

    fun reviews(ref: SetRef, source: SourceDto): Reviews = reviews.load(scope(ref, source))

    fun updateReviews(ref: SetRef, source: SourceDto, change: (Reviews) -> Reviews) {
        reviews.update(scope(ref, source), change)
    }

    fun articleIds(ref: SetRef, index: ArticleIndex?): List<String> =
        (workspace.articleIds(ref.profile, ref.set) + index?.articleIds.orEmpty()).distinct().sortedWith(NaturalOrder)

    fun pdf(ref: SetRef, id: String): Path = workspace.pdf(ref.profile, ref.set, id)

    fun location(ref: SetRef, source: SourceDto): Path = when (source) {
        SourceDto.RESULTS -> workspace.output(ref.profile, ref.set)
        SourceDto.GOLD -> workspace.gold(ref.profile, ref.set)
    }

    private fun scope(ref: SetRef, source: SourceDto) = ReviewScope(ref.profile, ref.set, source.name)

    private fun load(ref: SetRef, source: SourceDto): Loaded? {
        val directory = location(ref, source)
        val profile = profile(ref.profile)
        if (!hasTables(directory, profile)) return null
        val stamp = stamp(directory)
        return loaded.compute(directory) { _, existing ->
            existing?.takeIf { it.stamp == stamp } ?: run {
                val submission = repository.load(directory.toString(), profile).submission
                val audit = if (source == SourceDto.RESULTS) auditValues(submission) else null
                val index = ArticleIndex(audit?.submission ?: submission)
                val pages = FallbackPageTextSource(
                    JsonPageTextSource(workspace.pages(ref.profile)),
                    PdfPageTextSource(inspector) { pdf(ref, it) },
                )
                Loaded(stamp, index, audit) { QuoteChecker(profile, pages, normalizer) }
            }
        }
    }

    private fun hasTables(directory: Path, profile: Profile): Boolean = profile.tables.any { directory.resolve(it.file).exists() }

    private fun stamp(directory: Path): List<Long> = directory.listDirectoryEntries()
        .filterNot { it.isDirectory() }
        .sorted()
        .flatMap { listOf(it.getLastModifiedTime().toMillis(), it.fileSize()) }

    private companion object {
        val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]*")
    }
}
