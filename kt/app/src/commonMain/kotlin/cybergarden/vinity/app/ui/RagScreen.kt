package cybergarden.vinity.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cybergarden.vinity.api.RagExchangeDto
import cybergarden.vinity.api.RagPartDto
import cybergarden.vinity.api.RagSourceDto
import cybergarden.vinity.api.RagStatusDto
import cybergarden.vinity.app.AppStore
import cybergarden.vinity.app.openUrl
import kotlinx.coroutines.delay

private val Fragments = Word("фрагмент", "фрагмента", "фрагментов")
private val Citation = Regex("\\[(\\d+(?:\\s*[,;]\\s*\\d+)*)]")
private val Number = Regex("\\d+")
private const val SOURCE_LINES = 3
private const val HISTORY_LINES = 2
private val CONTROL = 36.dp
private val OPTION_ROW = 32.dp
private val BADGE_ROW = 24.dp
private val SOURCE_LIFT = 3.dp
private const val SECOND_MILLIS = 1000L
private val EXAMPLES = listOf(
    "Какие параметры поиска указаны в статье D001?",
    "Какие модели эмбеддингов используются в статьях?",
    "На каких наборах данных оценивают системы и какие метрики приводят?",
)

@Composable
fun RagScreen(store: AppStore) {
    val side = LocalWidth.current != Width.PHONE
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            if (side) {
                HistoryPanel(store, Modifier.width(300.dp).fillMaxHeight(), closable = false)
                Box(Modifier.width(1.dp).fillMaxHeight().background(C.divider))
            }
            ChatPanel(store, Modifier.weight(1f).fillMaxHeight(), side)
        }
        if (!side && store.ragHistoryOpen) HistoryPanel(store, Modifier.fillMaxSize(), closable = true)
    }
}

@Composable
private fun HistoryPanel(store: AppStore, modifier: Modifier, closable: Boolean) {
    Column(modifier.background(C.surface)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("История запросов", Modifier.weight(1f), style = T.h2)
            if (store.ragHistory.isNotEmpty()) Muted(store.ragHistory.size.toString(), small = true)
            if (closable) GhostButton(Glyph.CLOSE, "Закрыть", { store.ragHistoryOpen = false }, Modifier.padding(start = 8.dp))
        }
        if (store.ragHistory.isEmpty()) {
            Muted("Здесь появятся заданные вопросы. Нажмите на вопрос, чтобы вернуться к ответу.", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), small = true)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val state = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
                items(store.ragHistory.asReversed(), key = { it.id }) { exchange -> HistoryItem(store, exchange) }
            }
            ListScrollbar(state)
        }
        if (store.ragHistory.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.divider))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (store.ragConfirmClear) {
                    Text("Удалить всю историю?", Modifier.weight(1f), style = T.body)
                    DefaultButton("Отмена", { store.ragConfirmClear = false })
                    PrimaryButton("Удалить", store::clearRag)
                } else {
                    LinkButton("Очистить историю", { store.ragConfirmClear = true }, small = true)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryItem(store: AppStore, exchange: RagExchangeDto) {
    val focused = store.ragFocus == exchange.id
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(R8)
            .background(if (focused) C.primary1 else Color.Transparent)
            .press { store.focusRag(exchange.id) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(exchange.question, style = T.body, color = if (focused) C.primaryInk else C.fg, maxLines = HISTORY_LINES, overflow = TextOverflow.Ellipsis)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Cell(BADGE_ROW) { Muted(exchange.askedLabel, small = true) }
            Cell(BADGE_ROW) { Badge(corpus(exchange.csv)) }
            if (exchange.web) Cell(BADGE_ROW) { Badge("интернет", Tone.INFO) }
            if (exchange.base?.problem != null) Cell(BADGE_ROW) { Badge("ошибка", Tone.WARN) }
            Cell(BADGE_ROW) { LinkButton("Повторить", { store.repeatRag(exchange) }, Modifier.padding(start = 4.dp), small = true) }
        }
    }
}

@Composable
private fun ChatPanel(store: AppStore, modifier: Modifier, side: Boolean) {
    Column(modifier) {
        ChatWidth {
            Row(Modifier.padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("RAG-система", Modifier.weight(1f), style = T.h1)
                if (!side) DefaultButton("История", { store.ragHistoryOpen = true })
            }
        }
        ChatWidth { StatusLine(store, store.ragStatus) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val state = rememberLazyListState()
            val history = store.ragHistory
            LaunchedEffect(store.ragFocusRequests) {
                val index = history.indexOfFirst { it.id == store.ragFocus }
                if (index >= 0) state.animateScrollToItem(index)
            }
            LaunchedEffect(store.ragAsking) {
                if (store.ragAsking != null) state.animateScrollToItem(history.size)
            }
            LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                items(history, key = { it.id }) { exchange -> ChatWidth { Exchange(store, exchange) } }
                store.ragAsking?.let { question ->
                    item("asking") {
                        ChatWidth {
                            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                UserBubble(question, if (store.ragCsv) "таблицы CSV" else "статьи PDF")
                                Block {
                                    Waiting("Ищу фрагменты в базе и жду ответ модели", store::cancelRag, Modifier.padding(16.dp))
                                }
                            }
                        }
                    }
                }
                if (history.isEmpty() && store.ragAsking == null) item("intro") { ChatWidth { Intro(store) } }
            }
            ListScrollbar(state)
        }
        Composer(store)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusLine(store: AppStore, status: RagStatusDto?) {
    if (status == null) {
        Row(Modifier.heightIn(min = BADGE_ROW).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spinner()
            Muted("Проверяю базу и модель…", small = true)
        }
        return
    }
    FlowRow(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Cell(BADGE_ROW) { Param("База", if (status.problems.isEmpty()) "Qdrant · подключена" else "Qdrant · недоступна", if (status.problems.isEmpty()) C.ok else C.warn, status.qdrant) }
        status.pdfFragments?.let { Cell(BADGE_ROW) { Param("Статьи PDF", "${grouped(it)} ${plural(it.toInt(), Fragments).substringAfter(' ')}") } }
        status.csvFragments?.let { Cell(BADGE_ROW) { Param("Таблицы CSV", "${grouped(it)} ${plural(it.toInt(), Fragments).substringAfter(' ')}") } }
        status.model?.let { Cell(BADGE_ROW) { Param("Модель", it.substringAfterLast('/'), hint = it) } }
        status.embedding?.let { Cell(BADGE_ROW) { Param("Эмбеддинги", it.substringAfterLast('/'), hint = it) } }
        status.webSearch?.let { Cell(BADGE_ROW) { Param("Поиск в интернете", it) } }
        Cell(BADGE_ROW) { LinkButton(if (store.ragStatusLoading) "Проверяю…" else "Обновить", store::refreshRagStatus, small = true) }
    }
    if (status.problems.isNotEmpty()) {
        Alert(Tone.WARN, Glyph.WARN, Modifier.padding(bottom = 8.dp)) {
            status.problems.forEach { Text(it, style = T.body) }
        }
    }
}

@Composable
private fun Param(label: String, value: String, dot: Color? = null, hint: String? = null) {
    WithHint(hint) {
        Row(
            Modifier.clip(Pill).background(C.surface).border(1.dp, C.border, Pill).padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            dot?.let { Box(Modifier.size(7.dp).clip(CircleShape).background(it)) }
            Text(label, style = T.small, color = C.fg2, maxLines = 1)
            Text(value, style = T.smallMedium, color = C.fg, maxLines = 1)
        }
    }
}

private fun grouped(value: Long): String = value.toString().reversed().chunked(3).joinToString("\u2009").reversed()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Intro(store: AppStore) {
    Block(Modifier.padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Примеры вопросов", style = T.h2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EXAMPLES.forEach { example -> Chip(example, pressed = false, onClick = { store.askRag(example) }) }
            }
        }
    }
}

@Composable
private fun Exchange(store: AppStore, exchange: RagExchangeDto) {
    val meta = listOfNotNull(exchange.askedLabel, corpus(exchange.csv), exchange.topK?.let { plural(it, Fragments) }).joinToString(" · ")
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UserBubble(exchange.question, meta)
        Block {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PartHeader("Из базы статей", if (exchange.csv) "таблицы CSV" else "статьи PDF", Tone.PRI)
                exchange.base?.let { Part(it, web = false) } ?: Muted("Ответа нет", small = true)
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(C.divider))
                PartHeader("Из интернета", "открытые источники", Tone.INFO)
                val internet = exchange.internet
                when {
                    exchange.id in store.ragWebLoading -> Waiting("Ищу в открытых источниках и жду ответ модели", { store.cancelWeb(exchange.id) })
                    internet != null -> Part(internet, web = true)
                    exchange.web -> LinkButton("Запросить ответ из интернета", { store.searchWeb(exchange.id) }, small = true)
                    else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Muted("Поиск в интернете был выключен", small = true)
                        LinkButton("Искать в интернете", { store.searchWeb(exchange.id) }, small = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(question: String, meta: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(Modifier.widthIn(max = 640.dp).clip(R8).background(C.primary1).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(question, style = T.body, color = C.fg)
            Text(meta, style = T.small, color = C.fg2)
        }
    }
}

@Composable
private fun PartHeader(title: String, label: String, tone: Tone) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = T.h3)
        Badge(label, tone)
    }
}

@Composable
private fun Part(part: RagPartDto, web: Boolean) {
    part.problem?.let { problem ->
        Alert(Tone.WARN, Glyph.WARN) { Text(if (web) "Веб-ответ недоступен: $problem" else "Ответ из базы недоступен: $problem", style = T.body) }
    }
    part.answer?.let { Text(it, style = T.body) }
    if (part.sources.isEmpty()) return
    val cited = cited(part.answer)
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Источники", style = T.smallMedium, color = C.fg2)
        part.sources.forEach { source -> if (web) WebSource(source) else BaseSource(source, source.number in cited) }
    }
}

@Composable
private fun BaseSource(source: RagSourceDto, cited: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Badge("[${source.number}]", if (cited) Tone.PRI else Tone.NEUTRAL, hint = if (cited) "на него ссылается ответ" else null)
        Column(Modifier.weight(1f).padding(top = SOURCE_LIFT), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(source.reference, style = T.smallMedium)
            if (source.text.isNotBlank()) Text(source.text.replace('\n', ' '), style = T.small, color = C.fg2, maxLines = SOURCE_LINES, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WebSource(source: RagSourceDto) {
    val url = source.url
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Badge("[${source.number}]", Tone.INFO)
        Column(Modifier.weight(1f).padding(top = SOURCE_LIFT), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(source.title ?: source.reference, style = T.smallMedium)
            Text(listOfNotNull(url?.let(::domain), source.fetchedAt?.let { "получено ${it.take(16).replace('T', ' ')}" }).joinToString(" · "), style = T.small, color = C.fg2)
            if (source.text.isNotBlank()) Text(source.text.replace('\n', ' '), style = T.small, color = C.fg2, maxLines = SOURCE_LINES, overflow = TextOverflow.Ellipsis)
        }
        if (url != null) DefaultButton("Открыть", { openUrl(url) }, glyph = Glyph.LINK)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Composer(store: AppStore) {
    val busy = store.ragAsking != null
    Column(Modifier.fillMaxWidth().background(C.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.divider))
        ChatWidth {
            Column(Modifier.padding(top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InputBox(store.ragQuestion, { store.ragQuestion = it }, "Задайте вопрос по статьям в базе", Modifier.weight(1f).height(CONTROL), glyph = null, onDone = { store.askRag() })
                    PrimaryButton(if (busy) "Ищу…" else "Спросить", { store.askRag() }, enabled = !busy && store.ragQuestion.isNotBlank())
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Cell(OPTION_ROW) { Segmented(listOf(false to "Статьи (PDF)", true to "Таблицы (CSV)"), store.ragCsv, { store.ragCsv = it }) }
                    Cell(OPTION_ROW) { Toggle("Искать в интернете", store.ragWeb, { store.ragWeb = it }) }
                    Cell(OPTION_ROW) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Фрагментов", style = T.body, color = C.fg2)
                            Segmented(listOf<Pair<Int?, String>>(null to "авто", 3 to "3", 5 to "5", 8 to "8", 12 to "12"), store.ragTopK, { store.ragTopK = it })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Waiting(text: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SECOND_MILLIS)
            seconds++
        }
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Spinner()
        Muted("$text · $seconds с", Modifier.weight(1f))
        GhostButton(Glyph.CLOSE, "Отменить запрос", onCancel)
    }
}

@Composable
private fun Cell(height: Dp, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.heightIn(min = height), contentAlignment = Alignment.CenterStart, content = content)
}

@Composable
private fun ChatWidth(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(horizontal = 16.dp), content = content)
    }
}

private fun corpus(csv: Boolean): String = if (csv) "CSV" else "PDF"

private fun cited(answer: String?): Set<Int> = answer?.let { text ->
    Citation.findAll(text).flatMap { match -> Number.findAll(match.value).map { it.value.toInt() } }.toSet()
}.orEmpty()

private fun domain(url: String): String = url.substringAfter("://").substringBefore('/').removePrefix("www.")
