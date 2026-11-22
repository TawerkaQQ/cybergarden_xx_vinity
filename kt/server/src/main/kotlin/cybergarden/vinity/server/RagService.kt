package cybergarden.vinity.server

import cybergarden.vinity.api.RagAskDto
import cybergarden.vinity.api.RagExchangeDto
import cybergarden.vinity.api.RagPartDto
import cybergarden.vinity.api.RagSourceDto
import cybergarden.vinity.api.RagStatusDto
import cybergarden.vinity.data.rag.RagProcess
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Чат с модулем поиска `rag/`: ответ по базе, ответ по открытым источникам и история запросов. */
class RagService(private val process: RagProcess, private val file: Path) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    private val serializer = ListSerializer(RagExchangeDto.serializer())
    private val sequence = AtomicInteger()
    private var exchanges: MutableList<RagExchangeDto>? = null

    @Volatile
    private var cached: Pair<Long, RagStatusDto>? = null

    fun status(refresh: Boolean): RagStatusDto {
        val now = System.currentTimeMillis()
        cached?.takeIf { !refresh && now - it.first < STATUS_MILLIS }?.let { return it.second }
        val status = runCatching { process.status() }.fold(
            onSuccess = { payload ->
                val collections = payload["collections"]?.let { it as? JsonObject }
                val problems = payload["problems"]?.jsonArray.orEmpty().mapNotNull { it.text() }
                val pdf = collections?.get("pdf")?.let { (it as? JsonPrimitive)?.longOrNull }
                val csv = collections?.get("csv")?.let { (it as? JsonPrimitive)?.longOrNull }
                val empty = if (problems.isEmpty() && pdf == null && csv == null) listOf("В базе нет индексов: создайте их командой index.py") else emptyList()
                RagStatusDto(problems.isEmpty(), payload["model"]?.text() ?: process.model, payload["qdrant"]?.text(), pdf, csv, problems + empty)
            },
            onFailure = { failure -> RagStatusDto(false, process.model, null, null, null, listOf(failure.message ?: failure::class.simpleName.orEmpty())) },
        )
        cached = now to status
        return status
    }

    @Synchronized
    fun history(): List<RagExchangeDto> = loaded().toList()

    fun ask(request: RagAskDto): RagExchangeDto {
        val question = request.question.trim().take(MAX_QUESTION)
        require(question.isNotEmpty()) { "Вопрос пустой" }
        val topK = request.topK?.takeIf { it > 0 }
        val payload = buildJsonObject {
            put("question", question)
            put("csv", request.csv)
            topK?.let { put("top_k", it) }
        }
        val answered = runCatching { process.ask(payload) }
        val base = answered.fold(
            onSuccess = { result -> RagPartDto(result["answer"]?.text(), null, sources(result["sources"])) },
            onFailure = { failure -> RagPartDto(null, failure.message ?: failure::class.simpleName, emptyList()) },
        )
        if (answered.isFailure) cached = null
        val phrases = answered.getOrNull()?.get("phrases")?.let { it as? JsonArray }.orEmpty().mapNotNull { it.text() }
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
        val exchange = RagExchangeDto(
            id = "q${System.currentTimeMillis().toString(RADIX)}${sequence.incrementAndGet()}",
            askedAt = now.toString(),
            askedLabel = now.format(LABEL),
            question = question,
            csv = request.csv,
            topK = topK,
            web = request.web,
            phrases = phrases,
            base = base,
        )
        update { it += exchange }
        return exchange
    }

    fun web(id: String): RagExchangeDto? {
        val exchange = synchronized(this) { loaded().firstOrNull { it.id == id } } ?: return null
        val payload = buildJsonObject {
            put("question", exchange.question)
            putJsonArray("phrases") { exchange.phrases.forEach { add(JsonPrimitive(it)) } }
        }
        val internet = runCatching { process.web(payload) }.fold(
            onSuccess = { result -> RagPartDto(result["answer"]?.text(), null, sources(result["sources"])) },
            onFailure = { failure -> RagPartDto(null, failure.message ?: failure::class.simpleName, emptyList()) },
        )
        val updated = exchange.copy(web = true, internet = internet)
        update { list -> list.replaceAll { if (it.id == id) updated else it } }
        return updated
    }

    fun clear() = update { it.clear() }

    private fun sources(element: JsonElement?): List<RagSourceDto> = (element as? JsonArray).orEmpty().mapIndexedNotNull { index, item ->
        val source = item as? JsonObject ?: return@mapIndexedNotNull null
        val title = source["title"]?.text()
        val url = source["url"]?.text()
        RagSourceDto(
            number = source["number"]?.text()?.toIntOrNull() ?: (index + 1),
            reference = source["reference"]?.text() ?: listOfNotNull(title, url).joinToString(" — "),
            text = source["text"]?.text().orEmpty(),
            page = source["page"]?.text()?.toIntOrNull(),
            publicationId = source["publication_id"]?.text(),
            title = title,
            url = url,
            fetchedAt = source["fetched_at"]?.text(),
        )
    }

    @Synchronized
    private fun update(change: (MutableList<RagExchangeDto>) -> Unit) {
        val list = loaded()
        change(list)
        while (list.size > MAX_HISTORY) list.removeFirst()
        if (list.isEmpty()) {
            file.deleteIfExists()
        } else {
            file.parent.createDirectories()
            file.writeText(json.encodeToString(serializer, list), Charsets.UTF_8)
        }
    }

    private fun loaded(): MutableList<RagExchangeDto> = exchanges ?: run {
        val stored = if (file.exists()) runCatching { json.decodeFromString(serializer, file.readText(Charsets.UTF_8)) }.getOrDefault(emptyList()) else emptyList()
        stored.toMutableList().also { exchanges = it }
    }

    private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private companion object {
        const val STATUS_MILLIS = 30_000L
        const val MAX_QUESTION = 1000
        const val MAX_HISTORY = 200
        const val RADIX = 36
        val LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")
    }
}
