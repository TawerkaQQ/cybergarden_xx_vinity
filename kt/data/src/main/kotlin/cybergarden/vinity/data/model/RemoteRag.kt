package cybergarden.vinity.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class RagSource(val publicationId: String?, val page: Int?, val text: String, val reference: String?, val url: String?)

class RagAnswer(val answer: String, val sources: List<RagSource>)

class RemoteRag(private val endpoint: String) {

    private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(CONNECT_SECONDS)).build()

    fun ask(question: String, profile: String, set: String, limit: Int): RagAnswer {
        val body = buildJsonObject {
            put("question", question)
            put("profile", profile)
            put("set", set)
            put("top_k", limit)
        }
        val request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofSeconds(ANSWER_SECONDS))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), Charsets.UTF_8))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        check(response.statusCode() in OK) { "модуль РАГ ответил HTTP ${response.statusCode()}" }
        val payload = Json.parseToJsonElement(response.body()).jsonObject
        val answer = checkNotNull(payload.text("answer")) { "в ответе модуля РАГ нет поля answer" }
        val sources = payload["sources"]?.jsonArray.orEmpty().map { item ->
            val source = item.jsonObject
            RagSource(
                publicationId = source.text("publication_id") ?: source.text("source")?.let(::publicationFromFile),
                page = source.text("page")?.toIntOrNull(),
                text = source.text("text").orEmpty(),
                reference = source.text("reference") ?: source.text("source"),
                url = source.text("url"),
            )
        }
        return RagAnswer(answer, sources)
    }

    private fun JsonObject.text(key: String): String? = this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.takeIf { it.isNotBlank() }

    private fun publicationFromFile(source: String): String? =
        source.substringAfterLast('/').substringAfterLast('\\').takeIf { it.endsWith(PDF, ignoreCase = true) }?.dropLast(PDF.length)

    private companion object {
        const val PDF = ".pdf"
        const val CONNECT_SECONDS = 5L
        const val ANSWER_SECONDS = 240L
        val OK = 200..299
    }
}
