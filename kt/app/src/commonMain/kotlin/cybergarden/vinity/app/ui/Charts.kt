package cybergarden.vinity.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import cybergarden.vinity.api.BinDto
import cybergarden.vinity.api.HistogramDto
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class BarRow(val label: String, val value: Double, val text: String, val color: Color, val tip: String?, val onClick: (() -> Unit)?)

class ScatterPoint(val x: Double, val y: Double, val label: String, val flagged: Boolean, val tip: String?, val onClick: (() -> Unit)?)

class ScatterLine(val slope: Double, val offset: Double, val title: String, val limit: Boolean)

class Segment(val label: String, val count: Int, val color: Color, val ink: Color)

private val BarTop = 8.dp
private val BarRowHeight = 30.dp
private val BarHeight = 16.dp
private val BarBottom = 26.dp
private val BarRight = 56.dp

fun niceTicks(min: Double, max: Double, target: Int = 5): List<Double> {
    val low = minOf(min, max)
    val high = maxOf(min, max).let { if (it - low < 1e-12) low + 1.0 else it }
    val raw = (high - low) / target
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.firstOrNull { it >= raw } ?: (10 * magnitude)
    val start = floor(low / step + 1e-9) * step
    val end = ceil(high / step - 1e-9) * step
    val count = ((end - start) / step).roundToInt().coerceAtLeast(1)
    return (0..count).map { rounded(start + it * step) }
}

private fun rounded(value: Double): Double = (value * 1e6).roundToLong() / 1e6

@Composable
fun HBarChart(
    rows: List<BarRow>,
    max: Double,
    ticks: List<Double>,
    modifier: Modifier = Modifier,
    tickLabel: (Double) -> String = ::number,
    labelWidth: Dp = 190.dp,
) {
    if (rows.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val axis = T.axis.copy(color = C.fg2)
    val labelStyle = T.small.copy(color = C.fg)
    val grid = C.divider
    val density = LocalDensity.current
    val label = if (LocalWidth.current == Width.PHONE) minOf(labelWidth, 120.dp) else labelWidth
    val scale = if (max > 0) max else 1.0
    ChartFrame(
        height = BarTop + BarRowHeight * rows.size + BarBottom,
        modifier = modifier,
        hit = { point, _ ->
            val index = with(density) { floor((point.y - BarTop.toPx()) / BarRowHeight.toPx()).toInt() }
            index.takeIf { it in rows.indices }
        },
        tip = { rows[it].tip },
        clickable = { rows[it].onClick != null },
        onClick = { rows[it].onClick?.invoke() },
    ) { _ ->
        val left = label.toPx()
        val plot = (size.width - left - BarRight.toPx()).coerceAtLeast(1f)
        val top = BarTop.toPx()
        val rowHeight = BarRowHeight.toPx()
        val bar = BarHeight.toPx()
        val axisBottom = size.height - 22.dp.toPx()
        fun x(value: Double) = left + (value / scale).toFloat().coerceIn(0f, 1f) * plot
        ticks.forEach { tick ->
            drawLine(grid, Offset(x(tick), top), Offset(x(tick), axisBottom), 1.dp.toPx())
            val text = measurer.measure(tickLabel(tick), axis)
            drawText(text, topLeft = Offset(x(tick) - text.size.width / 2f, size.height - 6.dp.toPx() - text.size.height))
        }
        rows.forEachIndexed { index, row ->
            val y = top + index * rowHeight + (rowHeight - bar) / 2
            val name = measurer.measure(
                row.label,
                labelStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = (left - 10.dp.toPx()).roundToInt().coerceAtLeast(1)),
            )
            drawText(name, topLeft = Offset(left - 10.dp.toPx() - name.size.width, y + bar / 2 - name.size.height / 2f))
            val width = x(row.value) - left
            if (width > 0f) drawPath(roundedBar(left, y, width, bar, minOf(4.dp.toPx(), width), horizontal = true), row.color)
            val text = measurer.measure(row.text, labelStyle)
            drawText(text, topLeft = Offset(left + width + 6.dp.toPx(), y + bar / 2 - text.size.height / 2f))
        }
    }
}

private class Plot(val left: Float, val top: Float, val right: Float, val bottom: Float, val x0: Double, val x1: Double, val y0: Double, val y1: Double) {
    fun x(value: Double): Float = left + ((value - x0) / (x1 - x0)).toFloat() * (right - left)
    fun y(value: Double): Float = bottom - ((value - y0) / (y1 - y0)).toFloat() * (bottom - top)
}

private fun Density.scatterPlot(width: Float, height: Float, xTicks: List<Double>, yTicks: List<Double>) = Plot(
    left = 48.dp.toPx(),
    top = 16.dp.toPx(),
    right = width - 24.dp.toPx(),
    bottom = height - 40.dp.toPx(),
    x0 = xTicks.first(),
    x1 = xTicks.last(),
    y0 = yTicks.first(),
    y1 = yTicks.last(),
)

@Composable
fun ScatterChart(points: List<ScatterPoint>, lines: List<ScatterLine>, xLabel: String?, yLabel: String?, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val axis = T.axis.copy(color = C.fg2)
    val limitStyle = T.axis.copy(color = C.errInk)
    val labelStyle = T.small.copy(color = C.fg)
    val grid = C.divider
    val normal = C.c1
    val flagged = C.err
    val ring = C.surface
    val muted = C.fg2
    val density = LocalDensity.current
    val xTicks = remember(points) { niceTicks(points.minOf { it.x }, points.maxOf { it.x }, 6) }
    val yTicks = remember(points) { niceTicks(points.minOf { it.y }, points.maxOf { it.y }, 4) }
    ChartFrame(
        height = if (LocalWidth.current == Width.PHONE) 280.dp else 340.dp,
        modifier = modifier,
        hit = { point, area ->
            with(density) {
                val plot = scatterPlot(area.width.toFloat(), area.height.toFloat(), xTicks, yTicks)
                val distance = { index: Int -> hypot(plot.x(points[index].x) - point.x, plot.y(points[index].y) - point.y) }
                points.indices.minByOrNull(distance)?.takeIf { distance(it) <= 11.dp.toPx() }
            }
        },
        tip = { points[it].tip },
        clickable = { points[it].onClick != null },
        onClick = { points[it].onClick?.invoke() },
    ) { hovered ->
        val plot = scatterPlot(size.width, size.height, xTicks, yTicks)
        xTicks.forEach { tick ->
            drawLine(grid, Offset(plot.x(tick), plot.top), Offset(plot.x(tick), plot.bottom), 1.dp.toPx())
            val text = measurer.measure(number(tick), axis)
            drawText(text, topLeft = Offset(plot.x(tick) - text.size.width / 2f, plot.bottom + 4.dp.toPx()))
        }
        yTicks.forEach { tick ->
            drawLine(grid, Offset(plot.left, plot.y(tick)), Offset(plot.right, plot.y(tick)), 1.dp.toPx())
            val text = measurer.measure(number(tick), axis)
            drawText(text, topLeft = Offset(plot.left - 8.dp.toPx() - text.size.width, plot.y(tick) - text.size.height / 2f))
        }
        xLabel?.let {
            val text = measurer.measure(it, axis)
            drawText(text, topLeft = Offset((plot.left + plot.right) / 2 - text.size.width / 2f, size.height - text.size.height - 2.dp.toPx()))
        }
        yLabel?.let {
            val text = measurer.measure(it, axis)
            val pivot = Offset(10.dp.toPx(), (plot.top + plot.bottom) / 2)
            rotate(-90f, pivot) {
                drawText(text, topLeft = Offset(pivot.x - text.size.width / 2f, pivot.y - text.size.height / 2f))
            }
        }
        clipRect(plot.left, plot.top, plot.right, plot.bottom) {
            lines.forEach { line ->
                drawLine(
                    color = if (line.limit) flagged else muted,
                    start = Offset(plot.x(plot.x0), plot.y(line.slope * plot.x0 + line.offset)),
                    end = Offset(plot.x(plot.x1), plot.y(line.slope * plot.x1 + line.offset)),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = if (line.limit) null else PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                )
            }
        }
        lines.forEach { line ->
            val margin = (plot.y1 - plot.y0) * 0.08
            val start = if (line.limit) 0.7 else 0.95
            val at = generateSequence(start) { it - 0.08 }.takeWhile { it > 0.1 }
                .map { plot.x0 + (plot.x1 - plot.x0) * it }
                .firstOrNull { line.slope * it + line.offset in (plot.y0 + margin)..(plot.y1 - margin) } ?: return@forEach
            val text = measurer.measure(line.title, if (line.limit) limitStyle else axis)
            val y = plot.y(line.slope * at + line.offset)
            val top = if (line.limit) y - 8.dp.toPx() - text.size.height else y + 6.dp.toPx()
            drawText(text, topLeft = Offset(plot.x(at) - text.size.width, top))
        }
        points.forEachIndexed { index, point ->
            val center = Offset(plot.x(point.x), plot.y(point.y))
            val radius = if (index == hovered) 6.dp.toPx() else 5.dp.toPx()
            drawCircle(if (point.flagged) flagged else normal, radius, center)
            drawCircle(ring, radius, center, style = Stroke(2.dp.toPx()))
            if (point.flagged) {
                val text = measurer.measure(point.label, labelStyle)
                drawText(text, topLeft = Offset(center.x + 10.dp.toPx(), center.y - text.size.height / 2f))
            }
        }
    }
}

@Composable
fun Histogram(histogram: HistogramDto, min: Double?, max: Double?, onBin: (BinDto) -> Unit, modifier: Modifier = Modifier) {
    val bins = histogram.bins
    if (bins.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val axis = T.axis.copy(color = C.fg2)
    val valueStyle = T.small.copy(color = C.fg)
    val grid = C.divider
    val active = C.c1
    val inactive = C.fg3
    val density = LocalDensity.current
    val most = bins.maxOf { it.count }.coerceAtLeast(1)
    val yStep = if (most <= 5) 1 else ceil(most / 4.0).toInt()
    val yMax = ceil(most.toDouble() / yStep).toInt() * yStep
    val ranged = min != null || max != null
    val inside = { bin: BinDto -> !ranged || ((min == null || bin.to > min) && (max == null || bin.from <= max)) }
    ChartFrame(
        height = 170.dp,
        modifier = modifier,
        hit = { point, area ->
            with(density) {
                val left = 36.dp.toPx()
                val slot = (area.width - left - 12.dp.toPx()) / bins.size
                val index = floor((point.x - left) / slot).toInt()
                index.takeIf { it in bins.indices && point.y >= 14.dp.toPx() && point.y <= area.height - 34.dp.toPx() }
            }
        },
        tip = { val bin = bins[it]; "${number(bin.from)}–${number(bin.to)}: ${plural(bin.count, Values)}\nнажмите, чтобы отфильтровать" },
        clickable = { true },
        onClick = { onBin(bins[it]) },
    ) { _ ->
        val left = 36.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 14.dp.toPx()
        val bottom = size.height - 34.dp.toPx()
        val slot = (right - left) / bins.size
        val barWidth = minOf(24.dp.toPx(), slot - 4.dp.toPx()).coerceAtLeast(2f)
        fun y(count: Int) = bottom - count.toFloat() / yMax * (bottom - top)
        for (tick in 0..yMax step yStep) {
            drawLine(grid, Offset(left, y(tick)), Offset(right, y(tick)), 1.dp.toPx())
            val text = measurer.measure(tick.toString(), axis)
            drawText(text, topLeft = Offset(left - 6.dp.toPx() - text.size.width, y(tick) - text.size.height / 2f))
        }
        bins.forEachIndexed { index, bin ->
            val x = left + index * slot + (slot - barWidth) / 2
            if (bin.count > 0) {
                val barTop = y(bin.count)
                val height = bottom - barTop
                drawPath(roundedBar(x, barTop, barWidth, height, minOf(4.dp.toPx(), height), horizontal = false), if (inside(bin)) active else inactive)
            }
            if (bin.count == most) {
                val text = measurer.measure(bin.count.toString(), valueStyle)
                drawText(text, topLeft = Offset(x + barWidth / 2 - text.size.width / 2f, y(bin.count) - 5.dp.toPx() - text.size.height))
            }
        }
        val edges = bins.map { it.from } + bins.last().to
        val widest = edges.maxOf { measurer.measure(number(it), axis).size.width }
        val every = ceil((widest + 6.dp.toPx()) / slot).toInt().coerceAtLeast(1)
        edges.forEachIndexed { index, edge ->
            if (index % every == 0) {
                val text = measurer.measure(number(edge), axis)
                drawText(text, topLeft = Offset(left + index * slot - text.size.width / 2f, bottom + 8.dp.toPx()))
            }
        }
    }
}

@Composable
fun SegmentBar(parts: List<Segment>, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.count }.coerceAtLeast(1)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(28.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.filter { it.count > 0 }.forEach { part ->
                Box(
                    Modifier.weight(part.count.toFloat()).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(part.color).padding(start = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (part.count.toDouble() / total > 0.09) Text(part.count.toString(), style = T.small, color = part.ink, maxLines = 1)
                }
            }
        }
        ChartLegend(parts.map { "${it.label} — ${it.count}" to it.color })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartLegend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Text(label, Modifier.padding(start = 6.dp), style = T.small, color = C.fg2)
            }
        }
    }
}

@Composable
fun AdaptiveGrid(
    count: Int,
    minWidth: Dp,
    modifier: Modifier = Modifier,
    gap: Dp = 12.dp,
    equalHeight: Boolean = false,
    item: @Composable (Int, Modifier) -> Unit,
) {
    if (count == 0) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val perRow = ((maxWidth + gap) / (minWidth + gap)).toInt().coerceIn(1, count)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            (0 until count).chunked(perRow).forEach { chunk ->
                Row(
                    Modifier.fillMaxWidth().then(if (equalHeight) Modifier.height(IntrinsicSize.Min) else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    chunk.forEach { index -> item(index, Modifier.weight(1f).then(if (equalHeight) Modifier.fillMaxHeight() else Modifier)) }
                }
            }
        }
    }
}

@Composable
private fun ChartFrame(
    height: Dp,
    modifier: Modifier,
    hit: (Offset, IntSize) -> Int?,
    tip: (Int) -> String?,
    clickable: (Int) -> Boolean,
    onClick: (Int) -> Unit,
    draw: DrawScope.(Int?) -> Unit,
) {
    var pointer by remember { mutableStateOf<Offset?>(null) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val currentHit by rememberUpdatedState(hit)
    val currentClickable by rememberUpdatedState(clickable)
    val currentClick by rememberUpdatedState(onClick)
    val hovered = pointer?.let { hit(it, area) }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .onSizeChanged { area = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        pointer = if (event.type == PointerEventType.Exit) null else event.changes.firstOrNull()?.position
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> currentHit(offset, size)?.takeIf(currentClickable)?.let(currentClick) }
            }
            .pointerHoverIcon(if (hovered != null && clickable(hovered)) PointerIcon.Hand else PointerIcon.Default),
    ) {
        Canvas(Modifier.matchParentSize()) { draw(hovered) }
        TipLayer(hovered?.let(tip), pointer)
    }
}

@Composable
private fun TipLayer(text: String?, anchor: Offset?) {
    if (text.isNullOrBlank() || anchor == null) return
    val lines = text.split('\n')
    val gap = with(LocalDensity.current) { TIP_GAP.roundToPx() }
    Popup(popupPositionProvider = PointPosition(anchor, gap), properties = PopupProperties(focusable = false)) {
        DisableSelection {
            Column(Modifier.widthIn(max = TIP_WIDTH).shadow(6.dp, R8).clip(R8).background(C.surface).padding(horizontal = 10.dp, vertical = 8.dp)) {
                lines.forEachIndexed { index, line ->
                    Text(line, style = if (index == 0) T.smallMedium else T.small, color = C.fg)
                }
            }
        }
    }
}

private val TIP_GAP = 12.dp
private val TIP_WIDTH = 360.dp

private fun roundedBar(left: Float, top: Float, width: Float, height: Float, radius: Float, horizontal: Boolean): Path {
    val corner = CornerRadius(radius)
    val shape = if (horizontal) {
        RoundRect(left, top, left + width, top + height, topRightCornerRadius = corner, bottomRightCornerRadius = corner)
    } else {
        RoundRect(left, top, left + width, top + height, topLeftCornerRadius = corner, topRightCornerRadius = corner)
    }
    return Path().apply { addRoundRect(shape) }
}
