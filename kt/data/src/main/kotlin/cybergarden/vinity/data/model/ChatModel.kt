package cybergarden.vinity.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class ChatModel(private val baseUrl: String, val model: String, private val apiKey: String?) {

    private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(CONNECT_SECONDS)).build()

    fun complete(system: String, user: String, maxTokens: Int): String {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("temperature", 0)
            put("messages", JsonArray(listOf(message("system", system), message("user", user))))
            put("chat_template_kwargs", buildJsonObject { put("enable_thinking", false) })
        }
        val request = HttpRequest.newBuilder(URI.create(baseUrl.trimEnd('/') + COMPLETIONS))
            .timeout(Duration.ofSeconds(ANSWER_SECONDS))
            .header("Content-Type", "application/json")
            .apply { apiKey?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), Charsets.UTF_8))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        check(response.statusCode() in OK) { "модель ответила HTTP ${response.statusCode()}: ${response.body().take(ERROR_TEXT)}" }
        val content = Json.parseToJsonElement(response.body()).jsonObject["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
        return checkNotNull(content?.trim()?.takeIf { it.isNotEmpty() }) { "модель вернула пустой ответ" }
    }

    private fun message(role: String, content: String) = JsonObject(mapOf("role" to JsonPrimitive(role), "content" to JsonPrimitive(content)))

    private companion object {
        const val COMPLETIONS = "/chat/completions"
        const val CONNECT_SECONDS = 10L
        const val ANSWER_SECONDS = 180L
        const val ERROR_TEXT = 300
        val OK = 200..299
    }
}
