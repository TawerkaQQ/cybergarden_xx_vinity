package cybergarden.vinity.server

import cybergarden.vinity.api.ApiRoutes
import cybergarden.vinity.api.DatasetRef
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.vinityRoutes(service: DatasetService) {
    get(ApiRoutes.DATASETS) {
        call.respond(service.datasets())
    }

    get(ApiRoutes.DATASET) {
        val dataset = service.dataset(call.parameters.datasetRef())
        if (dataset == null) call.respond(HttpStatusCode.NotFound) else call.respond(dataset)
    }

    get(ApiRoutes.SCORE) {
        val strict = call.request.queryParameters[ApiRoutes.STRICT_PARAMETER].toBoolean()
        val score = service.score(call.parameters.datasetRef(), strict)
        if (score == null) call.respond(HttpStatusCode.NotFound) else call.respond(score)
    }

    get(ApiRoutes.PAGE) {
        val page = call.parameters[ApiRoutes.PAGE_PARAMETER]?.toIntOrNull()
        val image = page?.let {
            service.page(
                ref = call.parameters.datasetRef(),
                publicationId = call.parameters[ApiRoutes.PUBLICATION_PARAMETER].orEmpty(),
                page = it,
                fragment = call.request.queryParameters[ApiRoutes.FRAGMENT_PARAMETER],
            )
        }
        if (image == null) call.respond(HttpStatusCode.NotFound) else call.respondBytes(image, ContentType.Image.PNG)
    }

    service.webDistribution()?.let { distribution ->
        staticFiles("/", distribution.toFile()) {
            default("index.html")
        }
    }
}

private fun Parameters.datasetRef() =
    DatasetRef(get(ApiRoutes.PROFILE_PARAMETER).orEmpty(), get(ApiRoutes.SET_PARAMETER).orEmpty())
