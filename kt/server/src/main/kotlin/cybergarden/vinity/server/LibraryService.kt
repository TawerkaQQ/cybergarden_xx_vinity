package cybergarden.vinity.server

import cybergarden.vinity.api.ArticleDto
import cybergarden.vinity.api.ArticleSummaryDto
import cybergarden.vinity.api.ComparisonDto
import cybergarden.vinity.api.HistogramDto
import cybergarden.vinity.api.IssueReviewRequestDto
import cybergarden.vinity.api.OverlapDto
import cybergarden.vinity.api.ProcessingDto
import cybergarden.vinity.api.ProfileDto
import cybergarden.vinity.api.ProfileUploadDto
import cybergarden.vinity.api.RejectedFileDto
import cybergarden.vinity.api.ScoreDto
import cybergarden.vinity.api.SetDto
import cybergarden.vinity.api.SetRef
import cybergarden.vinity.api.SetSummaryDto
import cybergarden.vinity.api.SourceDto
import cybergarden.vinity.api.UploadDto
import cybergarden.vinity.api.ValueReviewRequestDto
import cybergarden.vinity.api.ValueTableDto
import cybergarden.vinity.data.Workspace
import cybergarden.vinity.data.csv.SubmissionArchive
import cybergarden.vinity.data.pdf.PdfUploads
import cybergarden.vinity.data.pdf.UploadedFile
import cybergarden.vinity.domain.model.IssueReview
import cybergarden.vinity.domain.model.MatchMode
import cybergarden.vinity.domain.model.Publications
import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.model.RecordKey
import cybergarden.vinity.domain.model.RenderedPage
import cybergarden.vinity.domain.model.Reviews
import cybergarden.vinity.domain.model.ValueReview
import cybergarden.vinity.domain.port.DocumentInspector
import cybergarden.vinity.domain.port.PageRenderer
import cybergarden.vinity.domain.port.TextNormalizer
import cybergarden.vinity.domain.usecase.ArticleIndex
import cybergarden.vinity.domain.usecase.QueryValues
import cybergarden.vinity.domain.usecase.ScoreSubmission
import cybergarden.vinity.domain.usecase.ValueCatalog
import cybergarden.vinity.domain.usecase.ValueQuery
import cybergarden.vinity.domain.usecase.ValueTable
import cybergarden.vinity.domain.usecase.key
import java.nio.file.Path
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.isRegularFile

class Archive(val name: String, val bytes: ByteArray)

class LibraryService(
    private val library: Library,
    private val renderer: PageRenderer,
    private val inspector: DocumentInspector,
    private val uploads: PdfUploads,
    private val normalizer: TextNormalizer,
    private val processing: ProcessingService,
) {
    private class QueryResult(val catalog: ValueCatalog, val query: ValueQuery, val table: ValueTable)

    private val score = ScoreSubmission()
    private val queryValues = QueryValues()

    @Volatile
    private var lastQuery: QueryResult? = null

    fun profiles(): List<ProfileDto> = library.profileNames().map { library.profile(it).toDto() }

    fun sets(): List<SetSummaryDto> = library.sets().map { ref ->
        SetSummaryDto(
            ref = ref,
            uploads = library.uploads(ref),
            profileTitle = library.profile(ref.profile).title,
            articleCount = library.articleIds(ref, library.index(ref, library.source(ref, null))).size,
            sources = library.sources(ref),
        )
    }

    fun set(ref: SetRef, requested: SourceDto?): SetDto? {
        if (!library.known(ref)) return null
        val source = library.source(ref, requested)
        val index = library.index(ref, source)
        val reviews = source?.let { library.reviews(ref, it) } ?: Reviews()
        return SetDto(
            ref = ref,
            uploads = library.uploads(ref),
            profile = library.profile(ref.profile).toDto(),
            sources = library.sources(ref),
            source = source,
            canCompare = library.canCompare(ref),
            pipeline = processing.info(),
            articles = library.articleIds(ref, index).map { summary(ref, it, index, reviews) },
        )
    }

    fun article(ref: SetRef, id: String, requested: SourceDto?): ArticleDto? {
        if (!library.known(ref, id)) return null
        val source = library.source(ref, requested)
        val catalog = library.catalog(ref, source)
        val index = catalog?.index
        val publication = index?.publication(id)
        val document = inspector.inspect(library.pdf(ref, id).toString())
        val spec = library.profile(ref.profile).values
        return ArticleDto(
            id = id,
            title = publication?.get(Publications.TITLE) ?: document?.title,
            authors = publication?.get(Publications.AUTHORS),
            year = publication?.get(Publications.YEAR),
            doi = publication?.get(Publications.DOI),
            processed = processed(publication, index?.values(id).orEmpty()),
            inScope = publication?.get(Publications.IN_SCOPE)?.toBooleanStrictOrNull(),
            exclusionReason = publication?.get(Publications.EXCLUSION_REASON),
            pageCount = document?.pageCount,
            pagesWithoutText = document?.pagesWithoutText.orEmpty(),
            textVariants = library.variants(ref, source, id),
            values = catalog?.of(id)?.map { it.toDto(spec) }.orEmpty(),
            issues = catalog?.let { current -> current.index.issues(id).map { it.toIssueDto(current.index, current.reviews) } }.orEmpty(),
            records = index?.recordTables(id)?.map { it.toDto() }.orEmpty(),
            processing = processing.status(ref, id),
            overlaps = index?.repeats?.of(id)?.mapNotNull { overlap -> overlap.other(id)?.let { OverlapDto(it, overlap.values) } }.orEmpty(),
        )
    }

    fun process(ref: SetRef, id: String): ProcessingDto? {
        if (!library.known(ref, id) || !library.pdf(ref, id).isRegularFile()) return null
        processing.enqueue(ref, listOf(id))
        return processing.status(ref, id)
    }

    fun comparison(ref: SetRef, id: String, strict: Boolean): ComparisonDto? {
        if (!library.known(ref, id) || !library.canCompare(ref)) return null
        val candidate = library.index(ref, SourceDto.RESULTS) ?: return null
        val gold = library.catalog(ref, SourceDto.GOLD) ?: return null
        val mode = if (strict) MatchMode.STRICT else MatchMode.LOOSE
        val result = score.article(candidate.submission, gold.index.submission, id, mode, normalizer)
        val spec = gold.index.profile.values
        return ComparisonDto(
            strict = strict,
            extra = result.extra.map { it.id },
            missed = result.missed.mapNotNull { gold.get(it.key())?.toDto(spec) },
            matchedIssues = result.matchedIssues.map { it.id },
            missedIssues = result.missedIssues.map { it.toIssueDto(gold.index, gold.reviews) },
        )
    }

    fun values(ref: SetRef, requested: SourceDto?, query: ValueQuery, offset: Int, limit: Int): ValueTableDto? {
        if (!library.known(ref)) return null
        val catalog = library.catalog(ref, library.source(ref, requested), complete = true)
            ?: return ValueTableDto(0, 0, offset, emptyList(), emptyList(), null)
        val table = lastQuery?.takeIf { it.catalog === catalog && it.query == query }?.table
            ?: queryValues(catalog, query).also { lastQuery = QueryResult(catalog, query, it) }
        val spec = catalog.index.profile.values
        val start = offset.coerceAtLeast(0)
        return ValueTableDto(
            total = table.matching.size,
            setTotal = catalog.facts.size,
            offset = start,
            values = table.matching.drop(start).take(limit).map { it.toDto(spec) },
            metrics = catalog.metrics.map { it.toDto() },
            histogram = table.histogram?.let { bins ->
                val metric = query.metrics.single()
                val unit = catalog.facts.firstOrNull { it.row[spec.metric] == metric }?.row?.get(spec.unit)
                HistogramDto(metric, unit, bins.map { it.toDto() })
            },
        )
    }

    fun review(ref: SetRef, requested: SourceDto?, request: ValueReviewRequestDto): Boolean {
        val source = reviewSource(ref, requested, request.publicationId, request.valueId) ?: return false
        val key = RecordKey(request.publicationId, request.valueId)
        library.updateReviews(ref, source) { reviews ->
            val state = request.state
            reviews.copy(values = if (state == null) reviews.values - key else reviews.values + (key to ValueReview(state.toVerdict(), request.value)))
        }
        return true
    }

    fun review(ref: SetRef, requested: SourceDto?, request: IssueReviewRequestDto): Boolean {
        val source = reviewSource(ref, requested, request.publicationId, request.issueId) ?: return false
        val key = RecordKey(request.publicationId, request.issueId)
        library.updateReviews(ref, source) { reviews ->
            val state = request.state
            reviews.copy(issues = if (state == null) reviews.issues - key else reviews.issues + (key to IssueReview(state.toVerdict(), request.reason)))
        }
        return true
    }

    fun page(ref: SetRef, id: String, page: Int, fragments: List<String>): RenderedPage? {
        if (!library.known(ref, id)) return null
        val sources = library.profile(ref.profile).sources
        val quotes = fragments.map { fragment -> fragment.takeIf { it.isNotBlank() }?.let(sources::quoteCandidates).orEmpty() }
        return renderer.render(library.pdf(ref, id).toString(), page, quotes)
    }

    fun score(ref: SetRef, strict: Boolean): ScoreDto? {
        if (!library.known(ref) || !library.canCompare(ref)) return null
        val candidate = library.index(ref, SourceDto.RESULTS) ?: return null
        val gold = library.index(ref, SourceDto.GOLD) ?: return null
        val mode = if (strict) MatchMode.STRICT else MatchMode.LOOSE
        return score(candidate.submission, gold.submission, mode).toDto()
    }

    fun upload(profile: String, files: List<UploadedFile>): UploadDto? {
        if (profile !in library.profileNames()) return null
        val outcome = uploads.save(profile, files)
        val ref = SetRef(profile, Workspace.UPLOADS)
        return UploadDto(
            ref = ref,
            added = outcome.added,
            rejected = outcome.rejected.map { RejectedFileDto(it.first, it.second) },
            queued = processing.enqueue(ref, outcome.added),
        )
    }

    fun installProfile(files: List<UploadedFile>): ProfileUploadDto {
        val profile = files.firstOrNull { it.name.endsWith(JSON, ignoreCase = true) }
            ?: return ProfileUploadDto(null, "Нужен файл profile.json")
        val rules = files.firstOrNull { it.name.endsWith(MARKDOWN, ignoreCase = true) }
        return try {
            val installed = library.workspace.profiles.install(profile.content.toString(Charsets.UTF_8), rules?.content?.toString(Charsets.UTF_8))
            ProfileUploadDto(installed.toDto(), null)
        } catch (invalid: IllegalArgumentException) {
            ProfileUploadDto(null, invalid.message ?: "Профиль не прошёл проверку")
        }
    }

    fun export(ref: SetRef, requested: SourceDto?): Archive? {
        if (!library.known(ref)) return null
        val source = library.source(ref, requested) ?: return null
        val files = library.profile(ref.profile).allTables.map { it.file }
        return Archive(
            name = "${ref.profile}-${ref.set}-${source.name.lowercase()}.zip",
            bytes = SubmissionArchive.zip(library.location(ref, source), files),
        )
    }

    fun webDistribution(): Path? = WEB_BUILDS.map { library.workspace.root.resolve(it) }
        .filter { it.isDirectory() }
        .maxByOrNull { build -> build.listDirectoryEntries().maxOfOrNull { it.getLastModifiedTime().toMillis() } ?: 0L }

    private fun reviewSource(ref: SetRef, requested: SourceDto?, publication: String, id: String): SourceDto? {
        if (!library.known(ref) || !SAFE_ID.matches(publication) || id.isBlank()) return null
        return library.source(ref, requested)
    }

    private fun summary(ref: SetRef, id: String, index: ArticleIndex?, reviews: Reviews): ArticleSummaryDto {
        val publication = index?.publication(id)
        val pdf = library.pdf(ref, id).toString()
        val document = inspector.cached(pdf)
        val values = index?.values(id).orEmpty()
        return ArticleSummaryDto(
            id = id,
            title = publication?.get(Publications.TITLE) ?: inspector.title(pdf),
            year = publication?.get(Publications.YEAR),
            authors = publication?.get(Publications.AUTHORS),
            doi = publication?.get(Publications.DOI),
            processed = processed(publication, values),
            inScope = publication?.get(Publications.IN_SCOPE)?.toBooleanStrictOrNull(),
            exclusionReason = publication?.get(Publications.EXCLUSION_REASON),
            pageCount = document?.pageCount,
            scan = document?.pagesWithoutText?.isNotEmpty() == true,
            valueCount = values.size,
            issueCount = index?.issues(id)?.count { !reviews.dismissed(it) } ?: 0,
            reviewed = values.count { reviews.value(it) != null },
            processing = processing.status(ref, id),
        )
    }

    private fun processed(publication: Record?, values: List<Record>): Boolean =
        publication?.get(Publications.IN_SCOPE) != null || values.isNotEmpty()

    private companion object {
        val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]*")
        const val JSON = ".json"
        const val MARKDOWN = ".md"
        val WEB_BUILDS = listOf("kt/app/build/dist/wasmJs/productionExecutable", "kt/app/build/dist/wasmJs/developmentExecutable")
    }
}
