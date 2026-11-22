package cybergarden.vinity.data.rag

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.exists

class RagSettings(val python: Path?, val baseUrl: String?, val model: String?, val apiKey: String?, val qdrant: String?)

/** Запускает модуль поиска `rag/api.py` на каждый запрос и читает его ответ в JSON. */
class RagProcess(private val root: Path, private val settings: RagSettings) {

    private val module = root.resolve(MODULE)

    val problem: String? get() = when {
        !module.resolve(SCRIPT).exists() -> "Нет модуля $MODULE/$SCRIPT в $root"
        settings.python == null || !settings.python.exists() ->
            "Не найден Python модуля: создайте $MODULE/.venv и установите $MODULE/requirements.txt или задайте vinity.rag.python"
        else -> null
    }

    val model: String? get() = settings.model

    fun status(): JsonObject = run(listOf("status"), null, STATUS_SECONDS)

    fun ask(request: JsonObject): JsonObject = run(listOf("ask"), request, ANSWER_SECONDS)

    fun web(request: JsonObject): JsonObject = run(listOf("web"), request, ANSWER_SECONDS)

    private fun run(arguments: List<String>, input: JsonObject?, seconds: Long): JsonObject {
        problem?.let { error(it) }
        val python = checkNotNull(settings.python)
        val builder = ProcessBuilder(listOf(python.toString(), "-u", SCRIPT) + arguments).directory(module.toFile())
        configure(builder.environment())
        val process = builder.start()
        val output = StringBuilder()
        val errors = ArrayDeque<String>()
        val reader = thread(isDaemon = true) { output.append(process.inputStream.bufferedReader(Charsets.UTF_8).readText()) }
        val errorReader = thread(isDaemon = true) {
            process.errorStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    synchronized(errors) {
                        errors.addLast(line)
                        if (errors.size > ERROR_LINES) errors.removeFirst()
                    }
                }
            }
        }
        process.outputStream.use { stream -> input?.let { stream.write(it.toString().toByteArray(Charsets.UTF_8)) } }
        if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("модуль RAG не ответил за $seconds с")
        }
        reader.join(JOIN_MILLIS)
        errorReader.join(JOIN_MILLIS)
        val line = output.lines().lastOrNull { it.isNotBlank() }
        val payload = line?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?: error("модуль RAG завершился с кодом ${process.exitValue()}: ${synchronized(errors) { errors.lastOrNull { it.isNotBlank() } } ?: "нет ответа"}")
        payload["error"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.let { error(it) }
        return payload
    }

    private fun configure(environment: MutableMap<String, String>) {
        environment["PYTHONIOENCODING"] = "utf-8"
        environment["PYTHONUNBUFFERED"] = "1"
        environment["HF_HUB_DISABLE_SYMLINKS_WARNING"] = "1"
        settings.baseUrl?.takeIf { it.isNotBlank() }?.let { environment["CHAT_BASE_URL"] = it }
        settings.model?.takeIf { it.isNotBlank() }?.let { environment["CHAT_PROFILE"] = it }
        environment["CHAT_API_KEY"] = settings.apiKey?.takeIf { it.isNotBlank() } ?: environment["CHAT_API_KEY"]?.takeIf { it.isNotBlank() } ?: NO_KEY
        if (environment["CHAT_REQUEST_OPTIONS"].isNullOrBlank()) environment["CHAT_REQUEST_OPTIONS"] = NO_THINKING
        settings.qdrant?.takeIf { it.isNotBlank() }?.let { environment["QDRANT_URL"] = it }
    }

    companion object {
        private const val MODULE = "rag"
        private const val SCRIPT = "api.py"
        private const val NO_KEY = "EMPTY"
        private const val NO_THINKING = """{"chat_template_kwargs":{"enable_thinking":false}}"""
        private const val STATUS_SECONDS = 60L
        private const val ANSWER_SECONDS = 240L
        private const val JOIN_MILLIS = 2_000L
        private const val ERROR_LINES = 20

        fun defaultPython(root: Path): Path? = listOf(".venv/Scripts/python.exe", ".venv/bin/python")
            .map { root.resolve(MODULE).resolve(it) }
            .firstOrNull { it.exists() }
    }
}
