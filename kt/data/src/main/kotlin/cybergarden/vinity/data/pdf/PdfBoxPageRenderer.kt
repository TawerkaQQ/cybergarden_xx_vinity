package cybergarden.vinity.data.pdf

import cybergarden.vinity.domain.model.Highlight
import cybergarden.vinity.domain.model.RenderedPage
import cybergarden.vinity.domain.port.PageRenderer
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime

class PdfBoxPageRenderer(private val dpi: Float = 144f) : PageRenderer {

    private data class PageKey(val path: Path, val modified: Long, val page: Int)

    private class Image(val png: ByteArray, val width: Int, val height: Int)

    private class Glyphs(val text: String, val left: FloatArray, val top: FloatArray, val right: FloatArray, val bottom: FloatArray)

    private class Box(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private val images = LruCache<PageKey, Image>(IMAGE_CACHE)
    private val glyphs = LruCache<PageKey, Glyphs>(GLYPH_CACHE)

    override fun render(document: String, page: Int, quotes: List<List<String>>): RenderedPage? {
        val path = Path.of(document)
        if (!path.exists()) return null
        val key = PageKey(path, path.getLastModifiedTime().toMillis(), page)
        var image = images[key]
        var text = glyphs[key]
        if (image == null || text == null) {
            Loader.loadPDF(path.toFile()).use { pdf ->
                if (page !in 1..pdf.numberOfPages) return null
                image = image ?: draw(pdf, page).also { images[key] = it }
                text = text ?: read(pdf, page).also { glyphs[key] = it }
            }
        }
        val rendered = image ?: return null
        val positions = text ?: return null
        val highlights = quotes.mapIndexedNotNull { index, candidates ->
            candidates.withIndex().firstNotNullOfOrNull { (candidate, fragment) ->
                positions.locate(fragment)?.let { highlight(index, it, rendered, candidate > 0) }
            }
        }
        return RenderedPage(rendered.png, highlights)
    }

    private fun draw(pdf: PDDocument, page: Int): Image {
        val image = PDFRenderer(pdf).renderImageWithDPI(page - 1, dpi)
        val png = ByteArrayOutputStream().use { out ->
            ImageIO.write(image, "png", out)
            out.toByteArray()
        }
        return Image(png, image.width, image.height)
    }

    private fun read(pdf: PDDocument, page: Int): Glyphs {
        val positions = mutableListOf<TextPosition>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                positions += textPositions
            }
        }
        stripper.startPage = page
        stripper.endPage = page
        runCatching { stripper.getText(pdf) }
        val text = StringBuilder()
        val owners = mutableListOf<TextPosition>()
        positions.forEach { position ->
            position.unicode.orEmpty().filterNot { it.isWhitespace() }.forEach { char ->
                text.append(char)
                owners += position
            }
        }
        return Glyphs(
            text = text.toString(),
            left = FloatArray(owners.size) { owners[it].xDirAdj },
            top = FloatArray(owners.size) { owners[it].yDirAdj - owners[it].heightDir },
            right = FloatArray(owners.size) { owners[it].xDirAdj + owners[it].widthDirAdj },
            bottom = FloatArray(owners.size) { owners[it].yDirAdj },
        )
    }

    private fun Glyphs.locate(fragment: String): Box? {
        val needle = fragment.filterNot { it.isWhitespace() }
        if (needle.isEmpty()) return null
        val start = text.indexOf(needle).takeIf { it >= 0 } ?: return null
        val range = start until start + needle.length
        return Box(
            left = range.minOf { left[it] },
            top = range.minOf { top[it] },
            right = range.maxOf { right[it] },
            bottom = range.maxOf { bottom[it] },
        )
    }

    private fun highlight(index: Int, box: Box, image: Image, rowLabel: Boolean): Highlight {
        val scale = dpi / POINTS_PER_INCH
        return Highlight(
            index = index,
            top = (box.top * scale / image.height).toDouble().coerceIn(0.0, 1.0),
            bottom = (box.bottom * scale / image.height).toDouble().coerceIn(0.0, 1.0),
            left = (box.left * scale / image.width).toDouble().coerceIn(0.0, 1.0),
            right = (box.right * scale / image.width).toDouble().coerceIn(0.0, 1.0),
            rowLabel = rowLabel,
        )
    }

    private companion object {
        const val POINTS_PER_INCH = 72f
        const val IMAGE_CACHE = 48
        const val GLYPH_CACHE = 256
    }
}

internal class LruCache<K, V>(private val capacity: Int) {
    private val entries = object : LinkedHashMap<K, V>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean = size > capacity
    }

    @Synchronized
    operator fun get(key: K): V? = entries[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        entries[key] = value
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}
