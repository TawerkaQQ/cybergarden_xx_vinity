package cybergarden.vinity.server

import cybergarden.vinity.api.ApiRoutes
import cybergarden.vinity.api.SetRef
import cybergarden.vinity.data.Workspace
import cybergarden.vinity.data.csv.CsvSubmissionRepository
import cybergarden.vinity.data.model.ChatModel
import cybergarden.vinity.data.model.RemoteRag
import cybergarden.vinity.data.pages.FallbackPageTextSource
import cybergarden.vinity.data.pages.JsonPageTextSource
import cybergarden.vinity.data.pages.PdfPageTextSource
import cybergarden.vinity.data.pdf.PdfBoxDocumentInspector
import cybergarden.vinity.data.pdf.PdfBoxPageRenderer
import cybergarden.vinity.data.pdf.PdfUploads
import cybergarden.vinity.data.pipeline.PipelineProcess
import cybergarden.vinity.data.pipeline.PipelineSettings
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
    val root = Path.of(System.getProperty("vinity.root") ?: "..").toAbsolutePath().normalize()
    val port = System.getProperty("vinity.port")?.toIntOrNull() ?: 8081
    val workspace = Workspace(root)
    val inspector = PdfBoxDocumentInspector()
    val library = Library(workspace, CsvSubmissionRepository(), inspector, JsonReviewRepository(workspace), QuoteNormalizer)
    val settings = pipelineSettings(root)
    val processing = ProcessingService(PipelineProcess(root, settings))
    val service = LibraryService(library, PdfBoxPageRenderer(), inspector, PdfUploads(workspace), QuoteNormalizer, processing)
    val insights = InsightService(library, inspector, QuoteNormalizer)
    val passports = PassportService(library, service, insights, inspector, ProvenanceReader(workspace), processing)
    val chat = settings.baseUrl?.let { url -> settings.model?.let { ChatModel(url, it, settings.apiKey) } }
    val pages = { ref: SetRef -> FallbackPageTextSource(JsonPageTextSource(workspace.pages(ref.profile)), PdfPageTextSource(inspector) { library.pdf(ref, it) }) }
    val questions = AskService(library, pages, chat, setting("vinity.rag.url", "RAG_URL")?.let(::RemoteRag))
    insights.warmUp()
    embeddedServer(Netty, port = port) { vinity(service, insights, passports, questions) }.start(wait = true)
}

private fun pipelineSettings(root: Path) = PipelineSettings(
    python = setting("vinity.python", null)?.let { Path.of(it) } ?: PipelineProcess.defaultPython(root),
    baseUrl = setting("vinity.model.url", "OPENAI_BASE_URL"),
    model = setting("vinity.model.name", "OPENAI_MODEL"),
    apiKey = setting("vinity.model.key", "OPENAI_API_KEY"),
    workers = setting("vinity.pipeline.workers", null)?.toIntOrNull() ?: DEFAULT_WORKERS,
)

private fun setting(property: String, variable: String?): String? =
    System.getProperty(property)?.takeIf { it.isNotBlank() } ?: variable?.let { System.getenv(it) }?.takeIf { it.isNotBlank() }

private const val DEFAULT_WORKERS = 4

fun Application.vinity(service: LibraryService, insights: InsightService, passports: PassportService, questions: AskService) {
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
        vinityRoutes(service, insights, passports, questions)
    }
}
