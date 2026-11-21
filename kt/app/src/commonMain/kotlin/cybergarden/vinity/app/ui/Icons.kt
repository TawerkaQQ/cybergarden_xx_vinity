package cybergarden.vinity.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Glyph(vararg val paths: String) {
    PLUS("M8 3v10M3 8h10"),
    SUN(circle(8f, 8f, 3f), "M8 1.5v1.5M8 13v1.5M1.5 8H3M13 8h1.5M3.4 3.4l1.1 1.1M11.5 11.5l1.1 1.1M3.4 12.6l1.1-1.1M11.5 4.5l1.1-1.1"),
    MOON("M13.5 9.6A5.6 5.6 0 1 1 6.4 2.5a4.4 4.4 0 0 0 7.1 7.1z"),
    UPLOAD("M8 11V2.5M4.5 6 8 2.5 11.5 6M2.5 10.5v2a1 1 0 0 0 1 1h9a1 1 0 0 0 1-1v-2"),
    SEARCH(circle(7f, 7f, 4.5f), "m10.5 10.5 3 3"),
    CLOSE("m4 4 8 8M12 4l-8 8"),
    RIGHT("m6 3.5 4.5 4.5L6 12.5"),
    LEFT("M10 3.5 5.5 8l4.5 4.5"),
    DOWN("m3.5 6 4.5 4.5L12.5 6"),
    FILE("M9.5 1.5h-5a1 1 0 0 0-1 1v11a1 1 0 0 0 1 1h7a1 1 0 0 0 1-1v-8.5zM9.5 1.5v3.5h3.5"),
    INFO(circle(8f, 8f, 6.5f), "M8 7.2v4M8 4.8v.1"),
    WARN("M8 1.8 15 14H1z", "M8 6.5v3.5M8 12v.1"),
    CHECK(circle(8f, 8f, 6.5f), "m5.2 8.2 2 2 3.8-4"),
    ZOOM_IN(circle(7f, 7f, 4.5f), "m10.5 10.5 3 3M5 7h4M7 5v4"),
    ZOOM_OUT(circle(7f, 7f, 4.5f), "m10.5 10.5 3 3M5 7h4"),
    TARGET(circle(8f, 8f, 5.5f), circle(8f, 8f, 1.5f)),
    LINK("M6.8 9.2a2.6 2.6 0 0 0 3.7 0l2.2-2.2a2.6 2.6 0 0 0-3.7-3.7l-.8.8M9.2 6.8a2.6 2.6 0 0 0-3.7 0L3.3 9a2.6 2.6 0 0 0 3.7 3.7l.8-.8"),
    EXPAND("M2.5 6V2.5H6M10 2.5h3.5V6M13.5 10v3.5H10M6 13.5H2.5V10"),
    EYE("M1.5 8S4 3.5 8 3.5 14.5 8 14.5 8 12 12.5 8 12.5 1.5 8 1.5 8z", circle(8f, 8f, 2f)),
    DIM("M3.5 2.5h9a1.5 1.5 0 0 1 1.5 1.5v8a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 2 12V4a1.5 1.5 0 0 1 1.5-1.5z", "M5.5 6h5a.5.5 0 0 1 .5.5v2a.5.5 0 0 1-.5.5h-5a.5.5 0 0 1-.5-.5v-2a.5.5 0 0 1 .5-.5z"),
    PLAY("M5 3.5v9l7-4.5z"),
    PLUG("M6 1.5v3M10 1.5v3M4 4.5h8v3a4 4 0 0 1-8 0zM8 11.5v3"),
    DOC("M14 3H6.5A1.5 1.5 0 0 0 5 4.5v15A1.5 1.5 0 0 0 6.5 21h11a1.5 1.5 0 0 0 1.5-1.5V8z", "M14 3v5h5M8.5 12.5h7M8.5 15.5h7M8.5 9.5h3"),
    DOWNLOAD("M8 2.5V11M4.5 7.5 8 11l3.5-3.5M2.5 10.5v2a1 1 0 0 0 1 1h9a1 1 0 0 0 1-1v-2");

    val box: Float get() = if (this == DOC) 24f else 16f
}

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

private val vectors = mutableMapOf<Glyph, ImageVector>()

private fun Glyph.vector(): ImageVector = vectors.getOrPut(this) {
    ImageVector.Builder(name, 16.dp, 16.dp, box, box).apply {
        paths.forEach { data ->
            addPath(
                pathData = addPathNodes(data),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f * box / 16f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}

@Composable
fun GlyphIcon(glyph: Glyph, tint: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Icon(glyph.vector(), contentDescription = null, tint = tint, modifier = modifier.size(size))
}
