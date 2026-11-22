package cybergarden.vinity.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object ModelCatalog {

    private const val TIMEOUT_SECONDS = 15L

    fun firstModel(baseUrl: String, apiKey: String?): String? = runCatching {
        val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS)).build()
        val request = HttpRequest.newBuilder(URI.create(baseUrl.trimEnd('/') + "/models"))
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .apply { apiKey?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        if (response.statusCode() !in 200..299) return@runCatching null
        Json.parseToJsonElement(response.body()).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
    }.getOrNull()
}
