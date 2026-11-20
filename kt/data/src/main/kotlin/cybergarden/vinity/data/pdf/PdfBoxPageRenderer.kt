package cybergarden.vinity.data.pdf

import cybergarden.vinity.domain.port.PageRenderer
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.awt.BasicStroke
import java.awt.Color
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.exists

class PdfBoxPageRenderer(private val dpi: Float = 110f) : PageRenderer {

    override fun render(document: String, page: Int, highlight: String?): ByteArray? {
        val path = Path.of(document)
        if (!path.exists()) return null
        Loader.loadPDF(path.toFile()).use { pdf ->
            if (page !in 1..pdf.numberOfPages) return null
            val image = PDFRenderer(pdf).renderImageWithDPI(page - 1, dpi)
            highlight?.let { locate(pdf, page, it) }?.let { image.frame(it) }
            return ByteArrayOutputStream().use { out ->
                ImageIO.write(image, "png", out)
                out.toByteArray()
            }
        }
    }

    private fun locate(pdf: PDDocument, page: Int, fragment: String): Rectangle2D? {
        val positions = mutableListOf<TextPosition>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                positions += textPositions
            }
        }
        stripper.startPage = page
        stripper.endPage = page
        stripper.getText(pdf)

        val text = StringBuilder()
        val owners = mutableListOf<TextPosition>()
        positions.forEach { position ->
            position.unicode.filterNot { it.isWhitespace() }.forEach { char ->
                text.append(char)
                owners += position
            }
        }
        val needle = fragment.filterNot { it.isWhitespace() }
        if (needle.isEmpty()) return null
        val start = text.indexOf(needle).takeIf { it >= 0 } ?: return null
        val hit = owners.subList(start, start + needle.length)

        val left = hit.minOf { it.xDirAdj }
        val top = hit.minOf { it.yDirAdj - it.heightDir }
        val right = hit.maxOf { it.xDirAdj + it.widthDirAdj }
        val bottom = hit.maxOf { it.yDirAdj }
        return Rectangle2D.Float(left, top, right - left, bottom - top)
    }

    private fun BufferedImage.frame(box: Rectangle2D) {
        val scale = dpi / POINTS_PER_INCH
        val x = (box.x * scale - PADDING).toInt()
        val y = (box.y * scale - PADDING).toInt()
        val width = (box.width * scale + 2 * PADDING).toInt()
        val height = (box.height * scale + 2 * PADDING).toInt()
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
        const val POINTS_PER_INCH = 72f
        const val PADDING = 3.0
    }
}
