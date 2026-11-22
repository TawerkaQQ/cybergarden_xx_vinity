package cybergarden.vinity.data.pipeline

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class PipelineSettings(val python: Path?, val baseUrl: String?, val model: String?, val apiKey: String?, val workers: Int)

class PipelineRun(val exitCode: Int, val lines: List<String>) {
    val succeeded: Boolean get() = exitCode == 0

    fun lastLine(): String? = lines.lastOrNull { it.isNotBlank() }?.trim()
}

class PipelineProcess(private val root: Path, private val settings: PipelineSettings) {

    val problem: String? = when {
        settings.python == null || !settings.python.exists() -> "Не найден Python конвейера: создайте pipeline/.venv или задайте vinity.python"
        !root.resolve(SOURCES).isDirectory() -> "Нет исходников конвейера в $SOURCES"
        settings.baseUrl.isNullOrBlank() -> "Не задан адрес модели: OPENAI_BASE_URL или vinity.model.url"
        else -> null
    }

    val model: String? get() = settings.model

    fun pages(profile: String, set: String, ids: List<String>, onLine: (String) -> Unit): PipelineRun =
        run(listOf("pages", "--profile", profile, "--set", set, "--only") + ids, onLine)

    fun extract(profile: String, set: String, ids: List<String>, onLine: (String) -> Unit): PipelineRun {
        val model = settings.model?.takeIf { it.isNotBlank() }?.let { listOf("--model", it) }.orEmpty()
        val connection = listOf("--base-url", settings.baseUrl.orEmpty(), "--workers", settings.workers.toString())
        return run(listOf("extract", "--profile", profile, "--set", set) + connection + model + listOf("--only") + ids, onLine)
    }

    fun assemble(profile: String, set: String, onLine: (String) -> Unit): PipelineRun =
        run(listOf("assemble", "--profile", profile, "--set", set), onLine)

    private fun run(arguments: List<String>, onLine: (String) -> Unit): PipelineRun {
        val python = checkNotNull(settings.python) { problem.orEmpty() }
        val builder = ProcessBuilder(listOf(python.toString(), "-u", "-m", MODULE) + arguments)
            .directory(root.toFile())
            .redirectErrorStream(true)
        val environment = builder.environment()
        val sources = root.resolve(SOURCES).toString()
        environment["PYTHONPATH"] = listOfNotNull(sources, environment["PYTHONPATH"]).joinToString(java.io.File.pathSeparator)
        environment["PYTHONIOENCODING"] = "utf-8"
        environment["PYTHONUNBUFFERED"] = "1"
        environment["OPENAI_API_KEY"] = settings.apiKey?.takeIf { it.isNotBlank() } ?: environment["OPENAI_API_KEY"]?.takeIf { it.isNotBlank() } ?: NO_KEY
        val process = builder.start()
        val lines = mutableListOf<String>()
        process.inputStream.bufferedReader(Charsets.UTF_8).useLines { sequence ->
            sequence.forEach { line ->
                lines += line
                onLine(line)
            }
        }
        return PipelineRun(process.waitFor(), lines)
    }

    companion object {
        private const val MODULE = "pipeline"
        private const val SOURCES = "pipeline/src"
        private const val NO_KEY = "EMPTY"

        fun defaultPython(root: Path): Path? = listOf(".venv/Scripts/python.exe", ".venv/bin/python")
            .map { root.resolve("pipeline").resolve(it) }
            .firstOrNull { it.exists() }
    }
}
