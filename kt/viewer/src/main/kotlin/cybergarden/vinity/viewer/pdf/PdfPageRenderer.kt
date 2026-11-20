package cybergarden.vinity.viewer.pdf

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.awt.BasicStroke
import java.awt.Color
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.io.path.exists

class PdfPageRenderer {

    fun render(pdf: Path, page: Int, fragment: String?): ImageBitmap? {
        if (!pdf.exists()) return null
        Loader.loadPDF(pdf.toFile()).use { document ->
            if (page !in 1..document.numberOfPages) return null
            val image = PDFRenderer(document).renderImageWithDPI(page - 1, DPI)
            fragment?.let { locate(document, page, it) }?.let { image.highlight(it) }
            return image.toComposeImageBitmap()
        }
    }

    private fun locate(document: PDDocument, page: Int, fragment: String): Rectangle2D? {
        val positions = mutableListOf<TextPosition>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                positions += textPositions
            }
        }
        stripper.startPage = page
        stripper.endPage = page
        stripper.getText(document)

        val text = StringBuilder()
        val owners = mutableListOf<TextPosition>()
        positions.filter { it.unicode.isNotBlank() }.forEach { position ->
            position.unicode.filterNot { it.isWhitespace() }.forEach { char ->
                text.append(char)
                owners += position
            }
        }
        val needle = fragment.filterNot { it.isWhitespace() }
        val start = text.indexOf(needle).takeIf { it >= 0 && needle.isNotEmpty() } ?: return null
        val hit = owners.subList(start, start + needle.length)

        val left = hit.minOf { it.xDirAdj }
        val top = hit.minOf { it.yDirAdj - it.heightDir }
        val right = hit.maxOf { it.xDirAdj + it.widthDirAdj }
        val bottom = hit.maxOf { it.yDirAdj }
        return Rectangle2D.Float(left, top, right - left, bottom - top)
    }

    private fun BufferedImage.highlight(box: Rectangle2D) {
        val scale = DPI / POINTS_PER_INCH
        val padding = 3.0
        val x = (box.x * scale - padding).toInt()
        val y = (box.y * scale - padding).toInt()
        val width = (box.width * scale + 2 * padding).toInt()
        val height = (box.height * scale + 2 * padding).toInt()
        createGraphics().apply {
            color = Color(255, 214, 0, 90)
            fillRect(x, y, width, height)
            color = Color(220, 30, 30)
            stroke = BasicStroke(3f)
            drawRect(x, y, width, height)
            dispose()
        }
    }

    private companion object {
        const val DPI = 110f
        const val POINTS_PER_INCH = 72f
    }
}
