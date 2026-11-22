package cybergarden.vinity.data.pages

import cybergarden.vinity.data.pdf.PdfBoxDocumentInspector
import cybergarden.vinity.domain.port.PageText
import cybergarden.vinity.domain.port.PageTextSource
import java.nio.file.Path

class PdfPageTextSource(
    private val inspector: PdfBoxDocumentInspector,
    private val document: (String) -> Path,
) : PageTextSource {

    override fun pages(publicationId: String): List<PageText>? =
        inspector.pageTexts(document(publicationId).toString())?.mapIndexed { index, text -> PageText(index + 1, mapOf(VARIANT to text)) }

    companion object {
        const val VARIANT = "pdfbox"
    }
}

class FallbackPageTextSource(private val primary: PageTextSource, private val fallback: PageTextSource) : PageTextSource {
    override fun pages(publicationId: String): List<PageText>? = primary.pages(publicationId) ?: fallback.pages(publicationId)
}
