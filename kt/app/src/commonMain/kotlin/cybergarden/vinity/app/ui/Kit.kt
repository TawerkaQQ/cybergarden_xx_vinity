package cybergarden.vinity.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.roundToInt

val R6 = RoundedCornerShape(6.dp)
val R8 = RoundedCornerShape(8.dp)
val Pill = RoundedCornerShape(16.dp)

enum class Tone { NEUTRAL, ERR, OK, WARN, PRI, MISS, INFO, OFF }

private class ToneColors(val bg: Color, val border: Color, val ink: Color, val icon: Color)

@Composable
private fun Tone.colors(): ToneColors = when (this) {
    Tone.NEUTRAL -> ToneColors(C.surface2, C.border, C.fg, C.fg2)
    Tone.ERR -> ToneColors(C.errBg, C.errBd, C.errInk, C.err)
    Tone.OK -> ToneColors(C.okBg, C.okBd, C.okInk, C.ok)
    Tone.WARN -> ToneColors(C.warnBg, C.warnBd, C.warnInk, C.warn)
    Tone.PRI, Tone.INFO -> ToneColors(C.primary1, C.primary3, C.primaryInk, C.primary)
    Tone.MISS -> ToneColors(Color.Transparent, C.border, C.fg2, C.fg3)
    Tone.OFF -> ToneColors(C.surface2, C.border, C.fg2, C.fg3)
}

fun Modifier.hand(): Modifier = pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    MaterialText(
        text,
        modifier.pointerHoverIcon(PointerIcon.Default, overrideDescendants = true),
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
        style = style,
    )
}

fun Modifier.press(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    clickable(source, indication = null, enabled = enabled, onClick = onClick).then(if (enabled) Modifier.hand() else Modifier)
}

fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = size.copy(width = size.width - stroke, height = size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

fun Modifier.topDivider(show: Boolean, color: Color, inset: Dp = 12.dp): Modifier =
    if (!show) this else drawBehind {
        drawLine(color, Offset(inset.toPx(), 0f), Offset(size.width, 0f), 1.dp.toPx())
    }

@Composable
fun hovered(source: MutableInteractionSource): Boolean {
    val value by source.collectIsHoveredAsState()
    return value
}

@Composable
fun Badge(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier, hint: String? = null) {
    val colors = tone.colors()
    WithHint(hint) {
        val shape = Modifier.clip(Pill).background(colors.bg)
        val edge = if (tone == Tone.MISS) Modifier.dashedBorder(colors.border, 16.dp) else Modifier.border(1.dp, colors.border, Pill)
        Text(
            text,
            modifier.then(shape).then(edge).padding(horizontal = 10.dp, vertical = 2.dp),
            style = T.small.copy(lineHeight = T.small.fontSize * 1.5f),
            color = colors.ink,
            maxLines = 1,
        )
    }
}

@Composable
fun Block(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(R8).background(C.surface), content = content)
}

@Composable
fun FieldRow(label: String, first: Boolean, modifier: Modifier = Modifier, value: @Composable ColumnScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().topDivider(!first, C.divider).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, Modifier.weight(1f), style = T.body, color = C.fg2)
        Column(Modifier.weight(if (LocalWidth.current == Width.PHONE) 2f else 1f), horizontalAlignment = Alignment.End, content = value)
    }
}

@Composable
fun FieldRow(label: String, value: String, first: Boolean, modifier: Modifier = Modifier) {
    FieldRow(label, first, modifier) { Text(value, style = T.body, textAlign = TextAlign.End) }
}

@Composable
fun SectionHeader(title: String, hint: String? = null, modifier: Modifier = Modifier, right: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.h3)
            hint?.let { Text(it, Modifier.subline(), style = T.small, color = C.fg2) }
        }
        right?.invoke()
    }
}

@Composable
fun PageHeader(title: String, subtitle: String?, modifier: Modifier = Modifier, right: (@Composable RowScope.() -> Unit)? = null) {
    if (LocalWidth.current == Width.PHONE) {
        Column(modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(title, style = T.h1)
            subtitle?.let { Text(it, Modifier.subline(), style = T.body, color = C.fg2) }
            right?.let { content -> Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
        }
        return
    }
    Row(modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.h1)
            subtitle?.let { Text(it, Modifier.subline(), style = T.body, color = C.fg2) }
        }
        right?.invoke(this)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, glyph: Glyph? = null, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val hover = hovered(source)
    Row(
        modifier
            .shadow(if (enabled) 1.dp else 0.dp, R8)
            .clip(R8)
            .background(if (!enabled) C.fg3 else if (hover) C.primaryHover else C.primary)
            .hoverable(source)
            .clickable(source, indication = null, enabled = enabled, onClick = onClick)
            .then(if (enabled) Modifier.hand() else Modifier)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        glyph?.let { GlyphIcon(it, Color.White) }
        Text(text, style = T.body, color = Color.White, maxLines = 1)
    }
}

@Composable
fun DefaultButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, glyph: Glyph? = null, enabled: Boolean = true, leading: (@Composable () -> Unit)? = null) {
    val source = remember { MutableInteractionSource() }
    val hover = hovered(source) && enabled
    val ink = if (!enabled) C.fg3 else if (hover) C.primary else C.fg
    Row(
        modifier
            .clip(R6)
            .background(C.surface)
            .border(1.dp, if (hover) C.primary else C.border, R6)
            .hoverable(source)
            .clickable(source, indication = null, enabled = enabled, onClick = onClick)
            .then(if (enabled) Modifier.hand() else Modifier)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        leading?.invoke()
        glyph?.let { GlyphIcon(it, ink) }
        Text(text, style = T.body, color = ink, maxLines = 1)
    }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, small: Boolean = false, glyph: Glyph? = null) {
    val source = remember { MutableInteractionSource() }
    val hover = hovered(source)
    val style = (if (small) T.small else T.body).copy(textDecoration = if (hover) TextDecoration.Underline else null)
    Row(
        modifier.hoverable(source).clickable(source, indication = null, onClick = onClick).hand(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        glyph?.let { GlyphIcon(it, C.primary) }
        Text(text, style = style, color = C.primary, maxLines = 1)
    }
}

@Composable
fun SquareButton(glyph: Glyph, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val hover = hovered(source) && enabled
    WithHint(description) {
        Box(
            modifier
                .size(36.dp)
                .shadow(if (enabled) 1.dp else 0.dp, R8)
                .clip(R8)
                .background(C.surface)
                .hoverable(source)
                .clickable(source, indication = null, enabled = enabled, onClick = onClick)
                .then(if (enabled) Modifier.hand() else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, if (!enabled) C.fg3 else if (hover) C.primary else C.fg)
        }
    }
}

@Composable
fun GhostButton(glyph: Glyph, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val source = remember { MutableInteractionSource() }
    val hover = hovered(source)
    WithHint(description) {
        Box(
            modifier
                .clip(R6)
                .background(if (hover) C.fill else Color.Transparent)
                .hoverable(source)
                .clickable(source, indication = null, onClick = onClick)
                .hand()
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, if (hover) C.fg else C.fg2, size = size)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <K> Segmented(options: List<Pair<K, String>>, selected: K, onSelect: (K) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier.clip(R8).background(C.fill).padding(2.dp)) {
        options.forEach { (key, label) ->
            val active = key == selected
            Text(
                label,
                Modifier
                    .then(if (active) Modifier.shadow(1.dp, R6) else Modifier)
                    .clip(R6)
                    .background(if (active) C.surface else Color.Transparent)
                    .press { onSelect(key) }
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                style = T.body,
                color = if (active) C.fg else C.fg2,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val thumb by animateDpAsState(if (checked) 20.dp else 2.dp)
    Row(modifier.press { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(40.dp).height(22.dp).clip(RoundedCornerShape(11.dp)).background(if (checked) C.primary else C.fg3)) {
            Box(Modifier.offset(x = thumb, y = 2.dp).size(18.dp).shadow(1.dp, CircleShape).clip(CircleShape).background(Color.White))
        }
        Text(label, style = T.body, maxLines = 1)
    }
}

@Composable
fun Chip(text: String, pressed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, hint: String? = null) {
    val source = remember { MutableInteractionSource() }
    val hover = hovered(source)
    val border = when {
        pressed -> C.primary3
        hover -> C.primary
        else -> C.border
    }
    val ink = when {
        pressed -> C.primaryInk
        hover -> C.primary
        else -> C.fg
    }
    WithHint(hint) {
        Text(
            text,
            modifier
                .clip(Pill)
                .background(if (pressed) C.primary1 else C.surface)
                .border(1.dp, border, Pill)
                .hoverable(source)
                .clickable(source, indication = null, onClick = onClick)
                .hand()
                .padding(horizontal = 10.dp, vertical = 1.dp),
            style = T.small.copy(lineHeight = T.small.fontSize * 1.66f),
            color = ink,
            maxLines = 1,
        )
    }
}

@Composable
fun InputBox(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    glyph: Glyph? = Glyph.SEARCH,
    enabled: Boolean = true,
    numeric: Boolean = false,
    onDone: (() -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        singleLine = true,
        interactionSource = source,
        textStyle = T.body.copy(color = if (enabled) C.fg else C.fg3),
        cursorBrush = SolidColor(C.primary),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier,
        decorationBox = { field ->
            Row(
                Modifier
                    .then(if (focused) Modifier.border(2.dp, C.primary1, R8) else Modifier)
                    .clip(R8)
                    .background(C.surface)
                    .border(1.dp, if (focused) C.primary else C.border, R8)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                glyph?.let { GlyphIcon(it, C.fg2) }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) MaterialText(placeholder, style = T.body, color = C.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    field()
                }
            }
        },
    )
}

@Composable
fun Alert(tone: Tone, glyph: Glyph, modifier: Modifier = Modifier, dashed: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val colors = tone.colors()
    Row(
        modifier
            .fillMaxWidth()
            .clip(R6)
            .background(if (dashed) Color.Transparent else colors.bg)
            .then(if (dashed) Modifier.dashedBorder(C.border, 6.dp) else Modifier.border(1.dp, colors.border, R6))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LeadingGlyph(glyph, colors.icon)
        Column(Modifier.weight(1f).alignByBaseline(), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

@Composable
fun Explain(text: String, modifier: Modifier = Modifier, bold: String? = null) {
    Row(modifier.fillMaxWidth().clip(R8).background(C.surface).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LeadingGlyph(Glyph.INFO, C.primary)
        Text(if (bold == null) text else "$bold $text", Modifier.weight(1f).alignByBaseline(), style = T.body)
    }
}

@Composable
fun RowScope.LeadingGlyph(glyph: Glyph, tint: Color) {
    val lift = with(LocalDensity.current) { GLYPH_LIFT.roundToPx() }
    GlyphIcon(glyph, tint, Modifier.alignBy { it.measuredHeight / 2 + lift })
}

private val GLYPH_LIFT = 4.5.dp

fun Modifier.subline(): Modifier = padding(top = SUBLINE_GAP)

private val SUBLINE_GAP = 4.dp

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Block(modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GlyphIcon(Glyph.DOC, C.fg3, Modifier.padding(bottom = 12.dp), size = 48.dp)
            Text(text, style = T.body, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp))
        }
    }
}

@Composable
fun GroupPill(text: String) {
    Box(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            Modifier.clip(Pill).background(C.surface.copy(alpha = 0.6f)).border(1.dp, C.bg, Pill).padding(horizontal = 10.dp, vertical = 4.dp),
            style = T.small.copy(lineHeight = T.small.fontSize * 1.2f),
        )
    }
}

class Stat(val label: String, val value: String, val color: Color)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsLine(stats: List<Stat>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        stats.forEach { stat ->
            Row {
                Text("${stat.label}: ", style = T.body, color = C.fg2)
                Text(stat.value, style = T.bodyMedium, color = stat.color)
            }
        }
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 14.dp) {
    CircularProgressIndicator(modifier.size(size), color = C.primary, trackColor = C.primary3, strokeWidth = 2.dp)
}

@Composable
fun ProgressLine(fraction: Float, ok: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.divider)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (ok) C.ok else C.primary))
    }
}

@Composable
fun Kbd(text: String) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(4.dp)).background(C.surface2).border(1.dp, C.border, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
        style = T.mono.copy(fontSize = T.axis.fontSize),
    )
}

class TabSpec<K>(val key: K, val label: String, val count: Int? = null)

@Composable
fun <K> UnderlineTabs(tabs: List<TabSpec<K>>, selected: K?, onSelect: (K) -> Unit, modifier: Modifier = Modifier, vertical: Dp = 10.dp) {
    Row(modifier.horizontalScroll(rememberScrollState())) {
        tabs.forEach { tab ->
            val active = tab.key == selected
            val ink = if (active) C.primary else C.fg
            val line = C.primary
            Row(
                Modifier
                    .press { onSelect(tab.key) }
                    .drawBehind {
                        if (active) {
                            val inset = 12.dp.toPx()
                            drawRoundRect(line, Offset(inset, size.height - 2.dp.toPx()), size.copy(width = size.width - 2 * inset, height = 2.dp.toPx()), CornerRadius(2.dp.toPx()))
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = vertical),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(tab.label, style = T.body, color = ink, maxLines = 1)
                tab.count?.let { Text(it.toString(), style = T.small, color = if (active) C.primary else C.fg2, maxLines = 1) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithHint(hint: String?, content: @Composable () -> Unit) {
    if (hint.isNullOrBlank()) {
        content()
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { DisableSelection { Text(hint, Modifier.widthIn(max = 320.dp), style = T.small, color = C.surface) } } },
        state = rememberTooltipState(),
        content = content,
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, small: Boolean = false, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier, style = if (small) T.small else T.body, color = C.fg2, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
fun Bold(text: String, modifier: Modifier = Modifier, color: Color = C.fg) {
    Text(text, modifier, style = T.body.copy(fontWeight = FontWeight.Medium), color = color)
}

class PointPosition(private val anchor: Offset, private val gap: Int, private val below: Boolean = false) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val pointerX = anchorBounds.left + anchor.x.roundToInt()
        val pointerY = anchorBounds.top + anchor.y.roundToInt()
        val right = pointerX + gap
        val x = if (right + popupContentSize.width <= windowSize.width) right else pointerX - gap - popupContentSize.width
        val above = pointerY - gap - popupContentSize.height
        val under = pointerY + gap
        val y = when {
            below && under + popupContentSize.height <= windowSize.height -> under
            below -> above
            above >= 0 -> above
            else -> under
        }
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}
