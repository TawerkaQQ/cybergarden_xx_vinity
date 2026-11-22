package cybergarden.vinity.app

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import cybergarden.vinity.api.AppScreen
import cybergarden.vinity.app.ui.Alert
import cybergarden.vinity.app.ui.AreasScreen
import cybergarden.vinity.app.ui.ArticleScreen
import cybergarden.vinity.app.ui.ArticlesScreen
import cybergarden.vinity.app.ui.AskScreen
import cybergarden.vinity.app.ui.C
import cybergarden.vinity.app.ui.EvidencePanel
import cybergarden.vinity.app.ui.FullscreenViewer
import cybergarden.vinity.app.ui.Glyph
import cybergarden.vinity.app.ui.LinkButton
import cybergarden.vinity.app.ui.LocalScrollTop
import cybergarden.vinity.app.ui.LocalWidth
import cybergarden.vinity.app.ui.OverviewScreen
import cybergarden.vinity.app.ui.QualityScreen
import cybergarden.vinity.app.ui.RagScreen
import cybergarden.vinity.app.ui.R8
import cybergarden.vinity.app.ui.Spinner
import cybergarden.vinity.app.ui.T
import cybergarden.vinity.app.ui.Text
import cybergarden.vinity.app.ui.Tone
import cybergarden.vinity.app.ui.TopBar
import cybergarden.vinity.app.ui.ValuesScreen
import cybergarden.vinity.app.ui.VinityTheme
import cybergarden.vinity.app.ui.Width
import cybergarden.vinity.app.ui.widthOf

@Composable
fun App(apiUrl: String) {
    val scope = rememberCoroutineScope()
    val store = remember { AppStore(VinityClient(apiUrl), scope) }
    LaunchedEffect(Unit) { store.start() }

    VinityTheme(store.darkTheme) {
        BoxWithConstraints(Modifier.fillMaxSize().background(C.bg)) {
            val width = widthOf(maxWidth)
            CompositionLocalProvider(LocalWidth provides width, LocalScrollTop provides store.scrollTop) {
                SelectionContainer { Box(Modifier.fillMaxSize()) { Content(store, width) } }
            }
        }
    }
}

@Composable
private fun Content(store: AppStore, width: Width) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(store.focusRequests) { runCatching { focus.requestFocus() } }
    val side = width == Width.WIDE || width == Width.NARROW
    Column(Modifier.fillMaxSize().focusRequester(focus).focusable().onKeyEvent { handleKey(store, it) }) {
        TopBar(store)
        store.error?.let { message ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Alert(Tone.ERR, Glyph.WARN) {
                    Text(message, style = T.body)
                    LinkButton("Скрыть", store::dismissError, small = true)
                }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).fillMaxHeight()) { Screen(store) }
            if (side && store.evidence != null) EvidencePanel(store, Modifier.fillMaxHeight(), docked = true)
        }
    }
    if (!side && store.evidence != null) EvidencePanel(store, Modifier.fillMaxSize(), docked = false)
    store.fullscreen?.let { FullscreenViewer(store, it) }
    store.toast?.let { Toast(it) }
}

@Composable
private fun Screen(store: AppStore) {
    if (store.set == null && store.screen != AppScreen.AREAS && store.screen != AppScreen.RAG) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(size = 24.dp) }
        return
    }
    when (store.screen) {
        AppScreen.ARTICLES -> ArticlesScreen(store)
        AppScreen.ARTICLE -> ArticleScreen(store)
        AppScreen.VALUES -> ValuesScreen(store)
        AppScreen.OVERVIEW -> OverviewScreen(store)
        AppScreen.QUALITY -> QualityScreen(store)
        AppScreen.AREAS -> AreasScreen(store)
        AppScreen.ASK -> AskScreen(store)
        AppScreen.RAG -> RagScreen(store)
    }
}

@Composable
private fun Toast(message: String) {
    Box(Modifier.fillMaxSize().padding(bottom = 24.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            message,
            Modifier.widthIn(max = 640.dp).shadow(6.dp, R8).clip(R8).background(C.fg).padding(horizontal = 16.dp, vertical = 8.dp),
            style = T.body,
            color = C.surface,
        )
    }
}

private fun handleKey(store: AppStore, event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) return false
    if (event.key == Key.Escape) {
        when {
            store.fullscreen != null -> store.closeFullscreen()
            store.fixing -> store.cancelFix()
            store.evidence != null -> store.closeEvidence()
            else -> return false
        }
        return true
    }
    if (store.evidenceValue() == null || store.fixing || store.fullscreen != null) return false
    when (event.key) {
        Key.A -> store.confirmValue()
        Key.R -> store.rejectValue()
        Key.E -> store.startFix()
        Key.J -> store.stepValue(1)
        Key.K -> store.stepValue(-1)
        else -> return false
    }
    return true
}
