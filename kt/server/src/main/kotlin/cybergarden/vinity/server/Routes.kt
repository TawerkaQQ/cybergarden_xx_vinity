package cybergarden.vinity.server

import cybergarden.vinity.api.ApiRoutes
import cybergarden.vinity.api.HighlightBox
import cybergarden.vinity.api.IssueReviewRequestDto
import cybergarden.vinity.api.ReviewFilterDto
import cybergarden.vinity.api.SetRef
import cybergarden.vinity.api.SourceDto
import cybergarden.vinity.api.ValueKindDto
import cybergarden.vinity.api.ValueReviewRequestDto
import cybergarden.vinity.api.ValueSortDto
import cybergarden.vinity.data.pdf.UploadedFile
import cybergarden.vinity.domain.usecase.ReviewFilter
import cybergarden.vinity.domain.usecase.ValueQuery
import cybergarden.vinity.domain.usecase.ValueSort
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.http.content.staticFiles
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.toByteArray

fun Route.vinityRoutes(library: LibraryService, insights: InsightService) {
    get(ApiRoutes.PROFILES) {
        call.respond(library.profiles())
    }

    post(ApiRoutes.PROFILES) {
        call.respond(library.installProfile(call.receiveFiles()))
    }

    get(ApiRoutes.SYSTEM) {
        call.respond(insights.system())
    }

    get(ApiRoutes.DEMO) {
        call.respond(insights.demo())
    }

    get(ApiRoutes.SETS) {
        call.respond(library.sets())
    }

    get(ApiRoutes.SET) {
        call.respondOrNotFound(library.set(call.parameters.setRef(), call.source()))
    }

    get(ApiRoutes.SCORE) {
        call.respondOrNotFound(library.score(call.parameters.setRef(), call.flag(ApiRoutes.STRICT_PARAMETER)))
    }

    get(ApiRoutes.QUALITY) {
        call.respondOrNotFound(insights.quality(call.parameters.setRef(), call.source()))
    }

    get(ApiRoutes.OVERVIEW) {
        call.respondOrNotFound(insights.overview(call.parameters.setRef(), call.source()))
    }

    get(ApiRoutes.EXPORT) {
        val archive = library.export(call.parameters.setRef(), call.source())
        if (archive == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, archive.name).toString(),
        )
        call.respondBytes(archive.bytes, ContentType.Application.Zip)
    }

    get(ApiRoutes.SET_VALUES) {
        val offset = call.request.queryParameters[ApiRoutes.OFFSET_PARAMETER]?.toIntOrNull() ?: 0
        val limit = (call.request.queryParameters[ApiRoutes.LIMIT_PARAMETER]?.toIntOrNull() ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        call.respondOrNotFound(library.values(call.parameters.setRef(), call.source(), call.valueQuery(), offset, limit))
    }

    post(ApiRoutes.REVIEW_VALUES) {
        val request = call.receive<ValueReviewRequestDto>()
        call.respond(if (library.review(call.parameters.setRef(), call.source(), request)) HttpStatusCode.OK else HttpStatusCode.NotFound)
    }

    post(ApiRoutes.REVIEW_ISSUES) {
        val request = call.receive<IssueReviewRequestDto>()
        call.respond(if (library.review(call.parameters.setRef(), call.source(), request)) HttpStatusCode.OK else HttpStatusCode.NotFound)
    }

    get(ApiRoutes.ARTICLE) {
        call.respondOrNotFound(library.article(call.parameters.setRef(), call.article(), call.source()))
    }

    get(ApiRoutes.COMPARISON) {
        call.respondOrNotFound(library.comparison(call.parameters.setRef(), call.article(), call.flag(ApiRoutes.STRICT_PARAMETER)))
    }

    get(ApiRoutes.PAGE) {
        val page = call.parameters[ApiRoutes.PAGE_PARAMETER]?.toIntOrNull()
        val fragments = call.request.queryParameters.getAll(ApiRoutes.FRAGMENT_PARAMETER).orEmpty()
        val rendered = page?.let { library.page(call.parameters.setRef(), call.article(), it, fragments) }
        if (rendered == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val boxes = rendered.highlights.map { HighlightBox(it.index, it.top, it.bottom, it.left, it.right, it.rowLabel) }
        if (boxes.isNotEmpty()) call.response.header(ApiRoutes.HIGHLIGHT_HEADER, HighlightBox.encode(boxes))
        call.respondBytes(rendered.png, ContentType.Image.PNG)
    }

    post(ApiRoutes.UPLOADS) {
        call.respondOrNotFound(library.upload(call.parameters[ApiRoutes.PROFILE_PARAMETER].orEmpty(), call.receiveFiles()))
    }

    library.webDistribution()?.let { distribution ->
        staticFiles("/", distribution.toFile()) {
            default("index.html")
        }
    }
}

private suspend fun ApplicationCall.receiveFiles(): List<UploadedFile> {
    val files = mutableListOf<UploadedFile>()
    receiveMultipart(formFieldLimit = MAX_UPLOAD_BYTES).forEachPart { part ->
        if (part is PartData.FileItem && part.name == ApiRoutes.FILE_PART) {
            files += UploadedFile(part.originalFileName.orEmpty(), part.provider().toByteArray())
        }
        part.dispose()
    }
    return files
}

private suspend inline fun <reified T : Any> ApplicationCall.respondOrNotFound(body: T?) {
    if (body == null) respond(HttpStatusCode.NotFound) else respond(body)
}

private fun Parameters.setRef() =
    SetRef(get(ApiRoutes.PROFILE_PARAMETER).orEmpty(), get(ApiRoutes.SET_PARAMETER).orEmpty())

private fun ApplicationCall.article(): String = parameters[ApiRoutes.ARTICLE_PARAMETER].orEmpty()

private fun ApplicationCall.source(): SourceDto? = enumParameter<SourceDto>(ApiRoutes.SOURCE_PARAMETER)

private fun ApplicationCall.flag(name: String): Boolean = request.queryParameters[name].toBoolean()

private inline fun <reified E : Enum<E>> ApplicationCall.enumParameter(name: String): E? =
    request.queryParameters[name]?.let { value -> enumValues<E>().firstOrNull { it.name.equals(value, ignoreCase = true) } }

private inline fun <reified E : Enum<E>> ApplicationCall.enumParameters(name: String): Set<E> =
    request.queryParameters.getAll(name).orEmpty().mapNotNull { value -> enumValues<E>().firstOrNull { it.name.equals(value, ignoreCase = true) } }.toSet()

private fun ApplicationCall.valueQuery(): ValueQuery {
    val query = request.queryParameters
    return ValueQuery(
        text = query[ApiRoutes.QUERY_PARAMETER].orEmpty(),
        metrics = query.getAll(ApiRoutes.METRIC_PARAMETER).orEmpty().filter { it.isNotEmpty() }.toSet(),
        kinds = enumParameters<ValueKindDto>(ApiRoutes.KIND_PARAMETER).map { it.toKind() }.toSet(),
        review = enumParameters<ReviewFilterDto>(ApiRoutes.REVIEW_PARAMETER).map { ReviewFilter.valueOf(it.name) }.toSet(),
        withIssues = flag(ApiRoutes.ISSUES_PARAMETER),
        hideDuplicates = flag(ApiRoutes.HIDE_DUPLICATES_PARAMETER),
        min = query[ApiRoutes.MIN_PARAMETER]?.replace(',', '.')?.toDoubleOrNull(),
        max = query[ApiRoutes.MAX_PARAMETER]?.replace(',', '.')?.toDoubleOrNull(),
        sort = enumParameter<ValueSortDto>(ApiRoutes.SORT_PARAMETER)?.let { ValueSort.valueOf(it.name) } ?: ValueSort.ARTICLE,
        descending = flag(ApiRoutes.DESCENDING_PARAMETER),
    )
}

private const val DEFAULT_LIMIT = 200
private const val MAX_LIMIT = 1000
private const val MAX_UPLOAD_BYTES = 200L * 1024 * 1024
