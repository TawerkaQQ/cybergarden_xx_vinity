package cybergarden.vinity.server

import cybergarden.vinity.api.ApiRoutes
import cybergarden.vinity.data.Workspace
import cybergarden.vinity.data.csv.CsvSubmissionRepository
import cybergarden.vinity.data.pdf.PdfBoxDocumentInspector
import cybergarden.vinity.data.pdf.PdfBoxPageRenderer
import cybergarden.vinity.data.pdf.PdfUploads
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
    val service = LibraryService(library, PdfBoxPageRenderer(), inspector, PdfUploads(workspace), QuoteNormalizer)
    val insights = InsightService(library, inspector, QuoteNormalizer)
    insights.warmUp()
    embeddedServer(Netty, port = port) { vinity(service, insights) }.start(wait = true)
}

fun Application.vinity(service: LibraryService, insights: InsightService) {
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
        vinityRoutes(service, insights)
    }
}
