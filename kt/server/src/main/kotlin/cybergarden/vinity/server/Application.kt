package cybergarden.vinity.server

import cybergarden.vinity.data.csv.CsvSubmissionRepository
import cybergarden.vinity.data.pdf.PdfBoxPageRenderer
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
    val service = DatasetService(root, CsvSubmissionRepository(), PdfBoxPageRenderer())
    embeddedServer(Netty, port = port) { vinity(service) }.start(wait = true)
}

fun Application.vinity(service: DatasetService) {
    install(ContentNegotiation) {
        json()
    }
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
    }
    routing {
        vinityRoutes(service)
    }
}
