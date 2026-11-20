package cybergarden.vinity.app

import cybergarden.vinity.api.ApiRoutes
import cybergarden.vinity.api.DatasetDto
import cybergarden.vinity.api.DatasetRef
import cybergarden.vinity.api.ScoreDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class VinityClient(val baseUrl: String) {

    private val http = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun datasets(): List<DatasetRef> = http.get(baseUrl + ApiRoutes.DATASETS).body()

    suspend fun dataset(ref: DatasetRef): DatasetDto = http.get(baseUrl + ApiRoutes.dataset(ref)).body()

    suspend fun score(ref: DatasetRef, strict: Boolean): ScoreDto? =
        http.get(baseUrl + ApiRoutes.score(ref)) {
            parameter(ApiRoutes.STRICT_PARAMETER, strict)
        }.takeIf { it.status.isSuccess() }?.body()

    suspend fun page(ref: DatasetRef, publicationId: String, page: Int, fragment: String?): ByteArray? =
        http.get(baseUrl + ApiRoutes.page(ref, publicationId, page)) {
            fragment?.let { parameter(ApiRoutes.FRAGMENT_PARAMETER, it) }
        }.takeIf { it.status.isSuccess() }?.body()
}
