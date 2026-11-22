package cybergarden.vinity.domain.model

data class DocumentInfo(
    val title: String?,
    val pageCount: Int,
    val pagesWithoutText: List<Int>,
)

data class Highlight(
    val index: Int,
    val top: Double,
    val bottom: Double,
    val left: Double,
    val right: Double,
    val rowLabel: Boolean,
)

class RenderedPage(val png: ByteArray, val highlights: List<Highlight>)
