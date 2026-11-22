package cybergarden.vinity.server

import cybergarden.vinity.api.ApiRoutes
import cybergarden.vinity.api.SetRef
import cybergarden.vinity.data.Workspace
import cybergarden.vinity.data.csv.CsvSubmissionRepository
import cybergarden.vinity.data.model.ChatModel
import cybergarden.vinity.data.model.ModelCatalog
import cybergarden.vinity.data.model.RemoteRag
import cybergarden.vinity.data.pages.FallbackPageTextSource
import cybergarden.vinity.data.pages.JsonPageTextSource
import cybergarden.vinity.data.pages.PdfPageTextSource
import cybergarden.vinity.data.pdf.PdfBoxDocumentInspector
import cybergarden.vinity.data.pdf.PdfBoxPageRenderer
import cybergarden.vinity.data.pdf.PdfUploads
import cybergarden.vinity.data.pipeline.PipelineProcess
import cybergarden.vinity.data.pipeline.PipelineSettings
import cybergarden.vinity.data.rag.DEFAULT_TIMEOUT_SECONDS
import cybergarden.vinity.data.rag.RagProcess
import cybergarden.vinity.data.rag.RagSettings
import cybergarden.vinity.data.provenance.ProvenanceReader
import cybergarden.vinity.data.review.JsonReviewRepository
import cybergarden.vinity.data.text.QuoteNormalizer
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import java.nio.file.Path

fun main() {
    val config = ServerConfig.load()
    val root = Path.of(config.value("root") ?: "..").toAbsolutePath().normalize()
    val port = config.value("port")?.toIntOrNull() ?: DEFAULT_PORT
    val workspace = Workspace(root)
    val inspector = PdfBoxDocumentInspector()
    val library = Library(workspace, CsvSubmissionRepository(), inspector, JsonReviewRepository(workspace), QuoteNormalizer)
    val settings = pipelineSettings(root, config)
    val processing = ProcessingService(PipelineProcess(root, settings))
    val web = config.value("web")?.let { Path.of(it).toAbsolutePath().normalize() }
    val service = LibraryService(library, PdfBoxPageRenderer(), inspector, PdfUploads(workspace), QuoteNormalizer, processing, web)
    val insights = InsightService(library, inspector, QuoteNormalizer)
    val passports = PassportService(library, service, insights, inspector, ProvenanceReader(workspace), processing)
    val chat = settings.baseUrl?.let { url -> settings.model?.let { ChatModel(url, it, settings.apiKey) } }
    val pages = { ref: SetRef -> FallbackPageTextSource(JsonPageTextSource(workspace.pages(ref.profile)), PdfPageTextSource(inspector) { library.pdf(ref, it) }) }
    val questions = AskService(library, pages, chat, config.value("rag.url", "RAG_URL")?.let(::RemoteRag))
    val ragSettings = RagSettings(config.value("rag.python")?.let { Path.of(it) } ?: RagProcess.defaultPython(root), settings.baseUrl, settings.model, settings.apiKey, config.value("rag.qdrant", "QDRANT_URL"), config.value("rag.timeout")?.toLongOrNull()?.takeIf { it > 0 } ?: DEFAULT_TIMEOUT_SECONDS)
    val rag = RagService(RagProcess(root, ragSettings), root.resolve("work").resolve("rag-chat").resolve("history.json"))
    println("Vinity: http://localhost:$port · данные $root · конфиг ${config.source ?: "не задан"} · модель ${settings.model ?: "не настроена"}")
    insights.warmUp()
    embeddedServer(Netty, port = port) { vinity(service, insights, passports, questions, rag) }.start(wait = true)
}

private fun pipelineSettings(root: Path, config: ServerConfig): PipelineSettings {
    val baseUrl = config.value("model.url", "OPENAI_BASE_URL")
    val apiKey = config.value("model.key", "OPENAI_API_KEY")
    return PipelineSettings(
        python = config.value("python")?.let { Path.of(it) } ?: PipelineProcess.defaultPython(root),
        baseUrl = baseUrl,
        model = config.value("model.name", "OPENAI_MODEL") ?: baseUrl?.let { ModelCatalog.firstModel(it, apiKey) },
        apiKey = apiKey,
        workers = config.value("pipeline.workers")?.toIntOrNull() ?: DEFAULT_WORKERS,
    )
}

private const val DEFAULT_PORT = 8081
private const val DEFAULT_WORKERS = 4

fun Application.vinity(service: LibraryService, insights: InsightService, passports: PassportService, questions: AskService, rag: RagService) {
    install(ContentNegotiation) {
        json()
    }
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
        exposeHeader(ApiRoutes.HIGHLIGHT_HEADER)
    }
    routing {
        vinityRoutes(service, insights, passports, questions, rag)
    }
}
