package cybergarden.vinity.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cybergarden.vinity.api.RagExchangeDto
import cybergarden.vinity.api.RagPartDto
import cybergarden.vinity.api.RagSourceDto
import cybergarden.vinity.api.RagStatusDto
import cybergarden.vinity.app.AppStore
import cybergarden.vinity.app.openUrl

private val Fragments = Word("фрагмент", "фрагмента", "фрагментов")
private val Citation = Regex("\\[(\\d+(?:\\s*[,;]\\s*\\d+)*)]")
private val Number = Regex("\\d+")
private const val SOURCE_LINES = 3
private const val HISTORY_LINES = 2
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
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("История запросов", Modifier.weight(1f), style = T.h2)
            if (store.ragHistory.isNotEmpty()) Muted(store.ragHistory.size.toString(), small = true)
            if (closable) GhostButton(Glyph.CLOSE, "Закрыть", { store.ragHistoryOpen = false }, Modifier.padding(start = 8.dp))
        }
        if (store.ragHistory.isEmpty()) {
            Muted("Здесь появятся заданные вопросы. Нажмите на вопрос, чтобы вернуться к ответу.", Modifier.padding(horizontal = 16.dp), small = true)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val state = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
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
            Muted(exchange.askedLabel, small = true)
            Badge(corpus(exchange.csv))
            if (exchange.web) Badge("интернет", Tone.INFO)
            if (exchange.base?.problem != null) Badge("ошибка", Tone.WARN)
            LinkButton("Повторить", { store.repeatRag(exchange) }, small = true)
        }
    }
}

@Composable
private fun ChatPanel(store: AppStore, modifier: Modifier, side: Boolean) {
    Column(modifier) {
        ChatWidth {
            Row(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("RAG-система", style = T.h1)
                    Muted("Ответ строится по найденным фрагментам базы статей и отдельно — по открытым источникам в интернете", small = true)
                }
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
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Spinner()
                                        Muted("Ищу фрагменты в базе и жду ответ модели — обычно 10–30 секунд")
                                    }
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
        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spinner()
            Muted("Проверяю базу и модель…", small = true)
        }
        return
    }
    val details = listOfNotNull(
        status.pdfFragments?.let { "статьи PDF — ${plural(it.toInt(), Fragments)}" },
        status.csvFragments?.let { "таблицы CSV — ${plural(it.toInt(), Fragments)}" },
        status.model?.let { "модель $it" },
    )
    if (status.problems.isEmpty()) {
        FlowRow(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Badge("База подключена", Tone.OK, hint = status.qdrant)
            Muted(details.joinToString(" · "), small = true)
            LinkButton(if (store.ragStatusLoading) "Проверяю…" else "Проверить снова", store::refreshRagStatus, small = true)
        }
        return
    }
    Alert(Tone.WARN, Glyph.WARN, Modifier.padding(bottom = 8.dp)) {
        Text("Часть системы недоступна: ответ из базы может не получиться, поиск в интернете работает, если доступна модель.", style = T.body)
        status.problems.forEach { Muted(it, small = true) }
        if (details.isNotEmpty()) Muted(details.joinToString(" · "), small = true)
        LinkButton(if (store.ragStatusLoading) "Проверяю…" else "Проверить снова", store::refreshRagStatus, small = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Intro(store: AppStore) {
    Block(Modifier.padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Задайте вопрос", style = T.h2)
            Explain("Система находит в базе фрагменты статей или строки таблиц, ближайшие по смыслу к вопросу, и отвечает только по ним — номер в скобках ведёт к источнику. Затем она ищет ответ в открытых источниках и показывает его отдельно, со ссылками на страницы.")
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
                    exchange.id in store.ragWebLoading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Spinner()
                        Muted("Ищу в открытых источниках и жду ответ модели…")
                    }
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
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Источники", style = T.smallMedium, color = C.fg2)
        part.sources.forEach { source -> if (web) WebSource(source) else BaseSource(source, source.number in cited) }
    }
}

@Composable
private fun BaseSource(source: RagSourceDto, cited: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Badge("[${source.number}]", if (cited) Tone.PRI else Tone.NEUTRAL, hint = if (cited) "на него ссылается ответ" else null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InputBox(store.ragQuestion, { store.ragQuestion = it }, "Задайте вопрос по статьям в базе", Modifier.weight(1f), glyph = null, onDone = { store.askRag() })
                    PrimaryButton(if (busy) "Ищу…" else "Спросить", { store.askRag() }, enabled = !busy && store.ragQuestion.isNotBlank())
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Segmented(listOf(false to "Статьи (PDF)", true to "Таблицы (CSV)"), store.ragCsv, { store.ragCsv = it })
                    Toggle("Искать в интернете", store.ragWeb, { store.ragWeb = it })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Muted("Фрагментов", small = true)
                        Segmented(listOf<Pair<Int?, String>>(null to "авто", 3 to "3", 5 to "5", 8 to "8", 12 to "12"), store.ragTopK, { store.ragTopK = it })
                    }
                }
            }
        }
    }
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
