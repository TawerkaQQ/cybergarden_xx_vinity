package cybergarden.vinity.app

import androidx.compose.ui.graphics.ImageBitmap
import cybergarden.vinity.api.ApiRoutes
import cybergarden.vinity.api.ArticleDto
import cybergarden.vinity.api.ComparisonDto
import cybergarden.vinity.api.DemoCaseDto
import cybergarden.vinity.api.HighlightBox
import cybergarden.vinity.api.IssueReviewRequestDto
import cybergarden.vinity.api.OverviewDto
import cybergarden.vinity.api.ProfileDto
import cybergarden.vinity.api.ProfileUploadDto
import cybergarden.vinity.api.QualityDto
import cybergarden.vinity.api.ReviewFilterDto
import cybergarden.vinity.api.ScoreDto
import cybergarden.vinity.api.SetDto
import cybergarden.vinity.api.SetRef
import cybergarden.vinity.api.SetSummaryDto
import cybergarden.vinity.api.SourceDto
import cybergarden.vinity.api.SystemDto
import cybergarden.vinity.api.UploadDto
import cybergarden.vinity.api.ValueKindDto
import cybergarden.vinity.api.ValueReviewRequestDto
import cybergarden.vinity.api.ValueSortDto
import cybergarden.vinity.api.ValueTableDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

data class ValueFilter(
    val query: String = "",
    val metrics: Set<String> = emptySet(),
    val kinds: Set<ValueKindDto> = emptySet(),
    val review: Set<ReviewFilterDto> = emptySet(),
    val onlyIssues: Boolean = false,
    val hideDuplicates: Boolean = false,
    val min: String = "",
    val max: String = "",
    val sort: ValueSortDto = ValueSortDto.ARTICLE,
    val descending: Boolean = false,
)

class PageImage(val bitmap: ImageBitmap, val boxes: List<HighlightBox>)

class VinityClient(val baseUrl: String) {

    private val json = Json { ignoreUnknownKeys = true }

    private val http = HttpClient {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun profiles(): List<ProfileDto> = http.get(baseUrl + ApiRoutes.PROFILES).body()

    suspend fun sets(): List<SetSummaryDto> = http.get(baseUrl + ApiRoutes.SETS).body()

    suspend fun system(): SystemDto = http.get(baseUrl + ApiRoutes.SYSTEM).body()

    suspend fun demo(): List<DemoCaseDto> = http.get(baseUrl + ApiRoutes.DEMO).body()

    suspend fun set(ref: SetRef, source: SourceDto?): SetDto =
        http.get(baseUrl + ApiRoutes.set(ref)) { source(source) }.body()

    suspend fun article(ref: SetRef, id: String, source: SourceDto?): ArticleDto =
        http.get(baseUrl + ApiRoutes.article(ref, id)) { source(source) }.body()

    suspend fun comparison(ref: SetRef, id: String, strict: Boolean): ComparisonDto? =
        http.get(baseUrl + ApiRoutes.comparison(ref, id)) { parameter(ApiRoutes.STRICT_PARAMETER, strict) }
            .takeIf { it.status.isSuccess() }?.body()

    suspend fun values(ref: SetRef, source: SourceDto?, filter: ValueFilter, offset: Int, limit: Int): ValueTableDto =
        http.get(baseUrl + ApiRoutes.setValues(ref)) {
            source(source)
            filter.query.takeIf { it.isNotBlank() }?.let { parameter(ApiRoutes.QUERY_PARAMETER, it.trim()) }
            filter.metrics.forEach { parameter(ApiRoutes.METRIC_PARAMETER, it) }
            filter.kinds.forEach { parameter(ApiRoutes.KIND_PARAMETER, it.name) }
            filter.review.forEach { parameter(ApiRoutes.REVIEW_PARAMETER, it.name) }
            if (filter.onlyIssues) parameter(ApiRoutes.ISSUES_PARAMETER, true)
            if (filter.hideDuplicates) parameter(ApiRoutes.HIDE_DUPLICATES_PARAMETER, true)
            filter.min.takeIf { it.isNotBlank() }?.let { parameter(ApiRoutes.MIN_PARAMETER, it.replace(',', '.')) }
            filter.max.takeIf { it.isNotBlank() }?.let { parameter(ApiRoutes.MAX_PARAMETER, it.replace(',', '.')) }
            parameter(ApiRoutes.SORT_PARAMETER, filter.sort.name)
            if (filter.descending) parameter(ApiRoutes.DESCENDING_PARAMETER, true)
            parameter(ApiRoutes.OFFSET_PARAMETER, offset)
            parameter(ApiRoutes.LIMIT_PARAMETER, limit)
        }.body()

    suspend fun page(ref: SetRef, id: String, page: Int, fragments: List<String>): PageImage? {
        val response = http.get(baseUrl + ApiRoutes.page(ref, id, page)) {
            fragments.forEach { parameter(ApiRoutes.FRAGMENT_PARAMETER, it) }
        }
        if (!response.status.isSuccess()) return null
        val bytes: ByteArray = response.body()
        return PageImage(bytes.toImageBitmap(), HighlightBox.decode(response.headers[ApiRoutes.HIGHLIGHT_HEADER]))
    }

    suspend fun score(ref: SetRef, strict: Boolean): ScoreDto? =
        http.get(baseUrl + ApiRoutes.score(ref)) { parameter(ApiRoutes.STRICT_PARAMETER, strict) }
            .takeIf { it.status.isSuccess() }?.body()

    suspend fun quality(ref: SetRef, source: SourceDto?): QualityDto? =
        http.get(baseUrl + ApiRoutes.quality(ref)) { source(source) }.takeIf { it.status.isSuccess() }?.body()

    suspend fun overview(ref: SetRef, source: SourceDto?): OverviewDto? =
        http.get(baseUrl + ApiRoutes.overview(ref)) { source(source) }.takeIf { it.status.isSuccess() }?.body()

    suspend fun reviewValue(ref: SetRef, source: SourceDto?, request: ValueReviewRequestDto) {
        val response = http.post(baseUrl + ApiRoutes.reviewValues(ref)) {
            source(source)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        check(response.status.isSuccess()) { "сервер ответил ${response.status}" }
    }

    suspend fun reviewIssue(ref: SetRef, source: SourceDto?, request: IssueReviewRequestDto) {
        val response = http.post(baseUrl + ApiRoutes.reviewIssues(ref)) {
            source(source)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        check(response.status.isSuccess()) { "сервер ответил ${response.status}" }
    }

    suspend fun uploadArticle(profile: String, file: LocalFile, onProgress: (Long, Long) -> Unit): UploadDto =
        json.decodeFromString(postFiles(baseUrl + ApiRoutes.uploads(profile), ApiRoutes.FILE_PART, listOf(file), onProgress))

    suspend fun uploadProfile(files: List<LocalFile>): ProfileUploadDto =
        json.decodeFromString(postFiles(baseUrl + ApiRoutes.PROFILES, ApiRoutes.FILE_PART, files) { _, _ -> })

    fun exportUrl(ref: SetRef, source: SourceDto?): String =
        baseUrl + ApiRoutes.export(ref) + (source?.let { "?${ApiRoutes.SOURCE_PARAMETER}=${it.name}" } ?: "")

    private fun HttpRequestBuilder.source(source: SourceDto?) {
        source?.let { parameter(ApiRoutes.SOURCE_PARAMETER, it.name) }
    }
}
