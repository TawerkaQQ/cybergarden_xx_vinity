package cybergarden.vinity.data.pdf

import cybergarden.vinity.domain.model.DocumentInfo
import cybergarden.vinity.domain.port.DocumentInspector
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime

class PdfBoxDocumentInspector : DocumentInspector {

    private class Inspection(val info: DocumentInfo, val pageTexts: List<String>)

    private val inspections = ConcurrentHashMap<Pair<Path, Long>, Inspection>()
    private val titles = ConcurrentHashMap<Pair<Path, Long>, String>()

    override fun inspect(document: String): DocumentInfo? = inspection(document)?.info

    override fun cached(document: String): DocumentInfo? {
        val path = Path.of(document)
        if (!path.exists()) return null
        return inspections[path.stamped()]?.info
    }

    override fun title(document: String): String? {
        val path = Path.of(document)
        if (!path.exists()) return null
        inspections[path.stamped()]?.let { return it.info.title }
        return titles.computeIfAbsent(path.stamped()) {
            runCatching { Loader.loadPDF(path.toFile()).use { it.metadataTitle() } }.getOrNull().orEmpty()
        }.ifEmpty { null }
    }

    fun pageTexts(document: String): List<String>? = inspection(document)?.pageTexts

    private fun inspection(document: String): Inspection? {
        val path = Path.of(document)
        if (!path.exists()) return null
        return inspections.computeIfAbsent(path.stamped()) { read(path) }
    }

    private fun Path.stamped() = this to getLastModifiedTime().toMillis()

    private fun read(path: Path): Inspection = Loader.loadPDF(path.toFile()).use { pdf ->
        val stripper = PDFTextStripper()
        val texts = (1..pdf.numberOfPages).map { page ->
            stripper.startPage = page
            stripper.endPage = page
            runCatching { stripper.getText(pdf) }.getOrDefault("")
        }
        Inspection(
            info = DocumentInfo(
                title = pdf.metadataTitle(),
                pageCount = pdf.numberOfPages,
                pagesWithoutText = texts.withIndex().filter { it.value.isBlank() }.map { it.index + 1 },
            ),
            pageTexts = texts,
        )
    }

    private fun PDDocument.metadataTitle(): String? = documentInformation?.title?.trim()?.takeIf { it.length >= MIN_TITLE }

    private companion object {
        const val MIN_TITLE = 8
    }
}
