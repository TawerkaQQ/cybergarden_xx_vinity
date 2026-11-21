package cybergarden.vinity.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Width { PHONE, TABLET, NARROW, WIDE }

val LocalWidth = staticCompositionLocalOf { Width.WIDE }

fun widthOf(width: Dp): Width = when {
    width < 560.dp -> Width.PHONE
    width < 900.dp -> Width.TABLET
    width < 1100.dp -> Width.NARROW
    else -> Width.WIDE
}

val LocalScrollTop = compositionLocalOf { 0 }

@Composable
fun ScreenList(key: Any, modifier: Modifier = Modifier, state: LazyListState = remember(key) { LazyListState() }, content: LazyListScope.() -> Unit) {
    ScrollToTop(state)
    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp), content = content)
        ListScrollbar(state)
    }
}

@Composable
fun ScrollToTop(state: LazyListState) {
    val request = LocalScrollTop.current
    val seen = remember { mutableIntStateOf(request) }
    LaunchedEffect(request) {
        if (request != seen.intValue) {
            seen.intValue = request
            state.animateScrollToItem(0)
        }
    }
}

@Composable
fun BoxScope.ListScrollbar(state: LazyListState) {
    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp))
}

@Composable
fun BoxScope.ColumnScrollbar(state: ScrollState) {
    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp))
}

fun LazyListScope.centered(key: Any? = null, content: @Composable () -> Unit) {
    item(key) { Centered { content() } }
}

@Composable
fun Centered(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 1240.dp).fillMaxWidth().padding(horizontal = 16.dp), content = content)
    }
}

@Composable
fun BlockPiece(first: Boolean, last: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val top = if (first) 8.dp else 0.dp
    val bottom = if (last) 8.dp else 0.dp
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(top, top, bottom, bottom)).background(C.surface),
        content = content,
    )
}

fun <T> LazyListScope.blockItems(items: List<T>, key: (T) -> Any, content: @Composable ColumnScope.(T, Boolean) -> Unit) {
    items.forEachIndexed { index, entry ->
        item(key(entry)) {
            Centered {
                BlockPiece(first = index == 0, last = index == items.lastIndex) {
                    content(entry, index == 0)
                }
            }
        }
    }
}
