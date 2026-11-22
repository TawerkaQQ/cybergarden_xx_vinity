package cybergarden.vinity.server

import cybergarden.vinity.api.PipelineDto
import cybergarden.vinity.api.ProcessingDto
import cybergarden.vinity.api.ProcessingStateDto
import cybergarden.vinity.api.SetRef
import cybergarden.vinity.data.pipeline.PipelineProcess
import cybergarden.vinity.data.pipeline.PipelineRun
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class ProcessingService(private val pipeline: PipelineProcess) {

    private data class Key(val ref: SetRef, val id: String)

    private class Job(val key: Key) {
        @Volatile var state = ProcessingStateDto.QUEUED
        @Volatile var step: String? = null
        @Volatile var problem: String? = null
        @Volatile var started: Long? = null
        @Volatile var finished: Long? = null
    }

    private val jobs = ConcurrentHashMap<Key, Job>()
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "pipeline").apply { isDaemon = true } }

    fun info(): PipelineDto = PipelineDto(pipeline.problem == null, pipeline.model, pipeline.problem)

    fun status(ref: SetRef, id: String): ProcessingDto? = jobs[Key(ref, id)]?.toDto()

    fun enqueue(ref: SetRef, ids: List<String>): List<String> {
        if (pipeline.problem != null) return emptyList()
        val queued = ids.distinct().filter { id ->
            val key = Key(ref, id)
            val current = jobs[key]
            if (current != null && current.state in ACTIVE) return@filter false
            jobs[key] = Job(key)
            true
        }
        if (queued.isNotEmpty()) worker.submit { drain(ref) }
        return queued
    }

    private fun drain(ref: SetRef) {
        val batch = jobs.values.filter { it.key.ref == ref && it.state == ProcessingStateDto.QUEUED }.sortedBy { it.key.id }
        if (batch.isEmpty()) return
        val ids = batch.map { it.key.id }
        val now = System.currentTimeMillis()
        batch.forEach {
            it.state = ProcessingStateDto.RUNNING
            it.started = now
            it.step = STEP_PAGES
        }
        try {
            process(ref, ids, batch)
        } catch (failure: Exception) {
            fail(batch.filter { it.state == ProcessingStateDto.RUNNING }, failure.message ?: failure::class.simpleName)
        }
    }

    private fun process(ref: SetRef, ids: List<String>, batch: List<Job>) {
        val pages = pipeline.pages(ref.profile, ref.set, ids) {}
        if (!pages.succeeded) return fail(batch, describe(pages))
        batch.forEach { it.step = STEP_EXTRACT }
        val failures = mutableMapOf<String, String>()
        val byId = batch.associateBy { it.key.id }
        val extract = pipeline.extract(ref.profile, ref.set, ids) { line ->
            val id = line.substringBefore(':', "")
            val job = byId[id] ?: return@extract
            val rest = line.substringAfter(':').trim()
            if (rest.startsWith(ERROR_MARK)) failures[id] = rest.removePrefix(ERROR_MARK).trim() else job.step = STEP_EXTRACTED
        }
        batch.filter { it.key.id in failures }.forEach { fail(listOf(it), failures.getValue(it.key.id)) }
        val extracted = batch.filter { it.key.id !in failures }
        if (extracted.isEmpty()) return
        if (!extract.succeeded && failures.isEmpty()) return fail(extracted, describe(extract))
        extracted.forEach { it.step = STEP_ASSEMBLE }
        val assemble = pipeline.assemble(ref.profile, ref.set) {}
        if (!assemble.succeeded) return fail(extracted, describe(assemble))
        val finished = System.currentTimeMillis()
        extracted.forEach {
            it.state = ProcessingStateDto.DONE
            it.step = null
            it.finished = finished
        }
    }

    private fun fail(jobs: List<Job>, problem: String?) {
        val finished = System.currentTimeMillis()
        jobs.forEach {
            it.state = ProcessingStateDto.FAILED
            it.problem = problem?.take(MAX_PROBLEM)
            it.finished = finished
        }
    }

    private fun describe(run: PipelineRun): String = run.lastLine() ?: "конвейер завершился с кодом ${run.exitCode}"

    private fun Job.toDto(): ProcessingDto {
        val seconds = started?.let { (((finished ?: System.currentTimeMillis()) - it) / 1000).toInt() }
        return ProcessingDto(state, step, problem, seconds)
    }

    private companion object {
        val ACTIVE = setOf(ProcessingStateDto.QUEUED, ProcessingStateDto.RUNNING)
        const val ERROR_MARK = "ошибка"
        const val STEP_PAGES = "текст страниц"
        const val STEP_EXTRACT = "извлечение моделью"
        const val STEP_EXTRACTED = "извлечено, ждёт остальные статьи"
        const val STEP_ASSEMBLE = "сборка таблиц и проверки"
        const val MAX_PROBLEM = 400
    }
}
