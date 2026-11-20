package cybergarden.vinity.data.pages

import cybergarden.vinity.domain.port.PageText
import cybergarden.vinity.domain.port.PageTextSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class JsonPageTextSource(private val directory: Path) : PageTextSource {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, List<PageText>?>()

    override fun pages(publicationId: String): List<PageText>? = cache.getOrPut(publicationId) {
        val path = directory.resolve("$publicationId.json")
        if (!path.exists()) return@getOrPut null
        json.decodeFromString<PagesDocument>(path.readText(Charsets.UTF_8)).pages.map { PageText(it.page, it.texts) }
    }

    @Serializable
    private data class PagesDocument(
        @SerialName("publication_id") val publicationId: String,
        val pages: List<PageDto>,
    )

    @Serializable
    private data class PageDto(val page: Int, val texts: Map<String, String>)
}
