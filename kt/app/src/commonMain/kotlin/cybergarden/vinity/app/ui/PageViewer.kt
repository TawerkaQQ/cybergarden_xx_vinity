package cybergarden.vinity.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cybergarden.vinity.api.HighlightBox
import cybergarden.vinity.app.AppStore
import cybergarden.vinity.app.Mark
import cybergarden.vinity.app.PageImage
import cybergarden.vinity.app.ViewerTarget

private sealed interface PageLoad {
    data object Loading : PageLoad
    data object Missing : PageLoad
    class Ready(val image: PageImage) : PageLoad
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Viewer(store: AppStore, target: ViewerTarget, modifier: Modifier = Modifier) {
    val page = store.viewerPages[target.key] ?: target.home
    val marks = target.marks[page].orEmpty()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    Column(modifier.fillMaxWidth().clip(R8).background(C.surface)) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PageNav(store, target, page)
            Spacer(Modifier.weight(1f))
            ZoomControl(store, full = false)
            SquareButton(Glyph.EXPAND, "Открыть страницу на весь экран", { store.openFullscreen(target) })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.divider))
        Box(Modifier.fillMaxWidth().heightIn(max = 640.dp).background(C.fill)) {
            Box(Modifier.fillMaxWidth().verticalScroll(scroll).padding(16.dp)) {
                PageCanvas(store, target.article, page, marks, store.zoom) { top ->
                    scroll.animateScrollTo((top - with(density) { 72.dp.toPx() }).toInt().coerceAtLeast(0))
                }
            }
            if (marks.isNotEmpty()) DimChip(store, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
        }
    }
}

@Composable
private fun PageNav(store: AppStore, target: ViewerTarget, page: Int) {
    val count = target.pageCount
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SquareButton(Glyph.LEFT, "Предыдущая страница", { store.turnViewer(target.key, page - 1) }, enabled = page > 1)
        Text(if (count != null) "Стр. $page из $count" else "Стр. $page", style = T.body)
        SquareButton(Glyph.RIGHT, "Следующая страница", { store.turnViewer(target.key, page + 1) }, enabled = count == null || page < count)
        if (page != target.home && target.marks.isNotEmpty()) LinkButton("Вернуться к цитате", { store.turnViewer(target.key, target.home) })
    }
}

@Composable
private fun ZoomControl(store: AppStore, full: Boolean) {
    val zoom = if (full) store.fullZoom else store.zoom
    Row(verticalAlignment = Alignment.CenterVertically) {
        SquareButton(Glyph.ZOOM_OUT, "Уменьшить", { store.changeZoom(-1, full) }, enabled = zoom > 0.61f)
        Text("${(zoom * 100).toInt()}%", Modifier.width(48.dp), style = T.small, textAlign = TextAlign.Center)
        SquareButton(Glyph.ZOOM_IN, "Увеличить", { store.changeZoom(1, full) }, enabled = zoom < 2.39f)
    }
}

@Composable
private fun DimChip(store: AppStore, modifier: Modifier) {
    Row(
        modifier.shadow(6.dp, Pill).clip(Pill).background(C.surface).press(onClick = store::toggleDim).padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GlyphIcon(if (store.dim) Glyph.EYE else Glyph.DIM, C.fg)
        Text(if (store.dim) "Показать всю страницу" else "Приглушить лишнее", style = T.small.copy(lineHeight = T.small.fontSize * 1.8f))
    }
}

@Composable
fun PageCanvas(store: AppStore, article: String, page: Int, marks: List<Mark>, zoom: Float, maxWidth: Dp = Dp.Unspecified, onFocus: suspend (Float) -> Unit) {
    val fragments = marks.map { it.fragment }
    val load by produceState<PageLoad>(PageLoad.Loading, store.ref, article, page, fragments) {
        value = PageLoad.Loading
        value = store.pageImage(article, page, fragments)?.let { PageLoad.Ready(it) } ?: PageLoad.Missing
    }
    when (val state = load) {
        PageLoad.Loading -> Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) { Spinner(size = 24.dp) }
        PageLoad.Missing -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text("Страница недоступна: PDF этой статьи не найден или не открывается", style = T.body, color = C.fg2)
        }
        is PageLoad.Ready -> BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val base = if (maxWidth == Dp.Unspecified) this.maxWidth else minOf(this.maxWidth, maxWidth)
            val width = base * zoom
            val bitmap = state.image.bitmap
            val ratio = bitmap.width.toFloat() / bitmap.height
            val density = LocalDensity.current
            val boxes = state.image.boxes
            val focus = boxes.firstOrNull { marks.getOrNull(it.index)?.focus == true } ?: boxes.firstOrNull()
            LaunchedEffect(bitmap, zoom, focus) {
                focus?.let { onFocus(with(density) { (width / ratio).toPx() } * it.top.toFloat()) }
            }
            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(width).aspectRatio(ratio).shadow(2.dp).background(Color.White)) {
                    Image(bitmap, contentDescription = "Страница $page", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    Highlights(boxes, marks, store.dim, Modifier.fillMaxSize().then(if (store.dim && boxes.isNotEmpty()) Modifier.press(onClick = store::showWholePage) else Modifier))
                }
                if (marks.isNotEmpty() && boxes.isEmpty()) {
                    Text(
                        "Цитату не удалось выделить: её нет в текстовом слое страницы. Так бывает со сканами и подписями внутри рисунков.",
                        Modifier.padding(top = 8.dp).widthIn(max = width),
                        style = T.small,
                        color = C.warnInk,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Highlights(boxes: List<HighlightBox>, marks: List<Mark>, dim: Boolean, modifier: Modifier) {
    val palette = C
    Canvas(modifier) {
        if (boxes.isEmpty()) return@Canvas
        if (dim) {
            val pad = size.height * 0.012f
            val bands = boxes.map { (it.top.toFloat() * size.height - pad).coerceAtLeast(0f) to (it.bottom.toFloat() * size.height + pad).coerceAtMost(size.height) }
                .sortedBy { it.first }
            val shade = Color.White.copy(alpha = 0.84f)
            var cursor = 0f
            bands.forEach { (top, bottom) ->
                if (top > cursor) drawRect(shade, Offset(0f, cursor), Size(size.width, top - cursor))
                cursor = maxOf(cursor, bottom)
            }
            if (cursor < size.height) drawRect(shade, Offset(0f, cursor), Size(size.width, size.height - cursor))
        }
        val inset = 3.dp.toPx()
        boxes.forEach { box ->
            val error = marks.getOrNull(box.index)?.error == true
            val left = box.left.toFloat() * size.width - inset
            val top = box.top.toFloat() * size.height - inset
            val rect = Size((box.right - box.left).toFloat() * size.width + 2 * inset, (box.bottom - box.top).toFloat() * size.height + 2 * inset)
            drawRect(if (error) palette.hlErr else palette.hl, Offset(left, top), rect)
            drawRect(if (error) palette.hlErrBorder else palette.hlBorder, Offset(left, top), rect, style = Stroke(2.dp.toPx()))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullscreenViewer(store: AppStore, target: ViewerTarget) {
    val page = store.viewerPages[target.key] ?: target.home
    val marks = target.marks[page].orEmpty()
    val title = store.articles[target.article]?.title ?: store.set?.articles?.firstOrNull { it.id == target.article }?.title.orEmpty()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).clickable(remember { MutableInteractionSource() }, indication = null) {}) {
        FlowRow(
            Modifier.fillMaxWidth().background(C.surface).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.weight(1f).widthIn(min = 200.dp)) {
                Text(target.article, style = T.bodyMedium)
                Text(" · $title", style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PageNav(store, target, page)
            if (marks.isNotEmpty()) DefaultButton(if (store.dim) "Показать всю страницу" else "Приглушить лишнее", store::toggleDim)
            ZoomControl(store, full = true)
            DefaultButton("Закрыть", store::closeFullscreen, glyph = Glyph.CLOSE)
            Kbd("Esc")
        }
        Box(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 48.dp), contentAlignment = Alignment.TopCenter) {
            PageCanvas(store, target.article, page, marks, store.fullZoom, maxWidth = 980.dp) { top ->
                scroll.animateScrollTo((top - with(density) { 120.dp.toPx() }).toInt().coerceAtLeast(0))
            }
        }
    }
}
