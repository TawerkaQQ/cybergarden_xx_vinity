package cybergarden.vinity.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cybergarden.vinity.api.ArticleSummaryDto
import cybergarden.vinity.api.SetDto
import cybergarden.vinity.api.SourceDto
import cybergarden.vinity.app.AppStore
import cybergarden.vinity.app.ListFilter
import cybergarden.vinity.app.UploadItem
import cybergarden.vinity.app.UploadStage

@Composable
fun ArticlesScreen(store: AppStore) {
    val set = store.set ?: return
    val wide = LocalWidth.current == Width.WIDE
    if (wide) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Row(Modifier.widthIn(max = 1240.dp).fillMaxHeight().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = rememberLazyListState(), contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp)) {
                    articleList(store, set, inset = false)
                }
                Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 40.dp)) {
                    UploadRail(store)
                }
            }
        }
    } else {
        ScreenList("articles-${set.ref.path}") {
            centered { UploadRail(store) }
            item { Box(Modifier.padding(top = 20.dp)) }
            articleList(store, set, inset = true)
        }
    }
}

private fun LazyListScope.articleList(store: AppStore, set: SetDto, inset: Boolean) {
    val wrap: LazyListScope.(Any, @Composable () -> Unit) -> Unit = { key, content ->
        item(key) { if (inset) Centered { content() } else content() }
    }
    wrap("head") { ArticlesHeader(store, set) }
    val groups = groupArticles(store, set)
    if (groups.isEmpty()) {
        wrap("empty") {
            EmptyState(
                if (set.articles.isEmpty()) "Здесь появятся статьи, загруженные вручную. Добавьте PDF справа." else "Ничего не нашлось. Измените поиск или фильтр.",
            )
        }
        return
    }
    groups.forEach { (title, articles) ->
        wrap("group-$title") { GroupPill("$title · ${articles.size}") }
        articles.forEachIndexed { index, article ->
            wrap("article-${article.id}") {
                BlockPiece(first = index == 0, last = index == articles.lastIndex) {
                    ArticleRow(store, article, index == 0)
                }
            }
        }
    }
}

private fun groupArticles(store: AppStore, set: SetDto): List<Pair<String, List<ArticleSummaryDto>>> {
    val query = store.listQuery.trim()
    val shown = set.articles.filter { article ->
        val matches = query.isEmpty() || listOfNotNull(article.id, article.title, article.doi, article.authors).any { it.contains(query, ignoreCase = true) }
        matches && when (store.listFilter) {
            ListFilter.ALL -> true
            ListFilter.ISSUES -> article.issueCount > 0
            ListFilter.OUT -> article.inScope == false
        }
    }
    val fresh = shown.filter { store.isUnseen(it.id) }
    val rest = shown - fresh.toSet()
    return listOf(
        "Новые" to fresh,
        "Есть замечания" to rest.filter { it.processed && it.inScope != false && it.issueCount > 0 },
        "Без замечаний" to rest.filter { it.processed && it.inScope != false && it.issueCount == 0 },
        "Не обработаны" to rest.filter { !it.processed },
        "Вне области" to rest.filter { it.processed && it.inScope == false },
    ).filter { it.second.isNotEmpty() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArticlesHeader(store: AppStore, set: SetDto) {
    val articles = set.articles
    val values = articles.sumOf { it.valueCount }
    Column {
        PageHeader("Статьи", "${set.profile.title} · набор ${setTitle(set.ref, set.uploads)} · ${sourceNote(set)}")
        StatsLine(
            listOf(
                Stat("Всего", articles.size.toString(), C.fg),
                Stat("В области", articles.count { it.processed && it.inScope != false }.toString(), C.okInk),
                Stat("Значений", values.toString(), C.primaryInk),
                Stat("Замечаний", articles.sumOf { it.issueCount }.toString(), C.errInk),
                Stat("Сканов", articles.count { it.scan }.toString(), C.warnInk),
                Stat("Проверено референтом", "${articles.sumOf { it.reviewed }} из $values", C.fg),
            ),
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InputBox(store.listQuery, { store.listQuery = it }, "Название, ID или DOI", Modifier.widthIn(min = 220.dp, max = 360.dp))
            Segmented(
                listOf(ListFilter.ALL to "Все", ListFilter.ISSUES to "С замечаниями", ListFilter.OUT to "Вне области"),
                store.listFilter,
                { store.listFilter = it },
            )
        }
    }
}

fun sourceNote(set: SetDto): String = when {
    set.source == SourceDto.GOLD -> "показан эталон экспертов"
    set.source == SourceDto.RESULTS && set.canCompare -> "результат системы, эталон экспертов есть"
    set.source == SourceDto.RESULTS -> "результат системы, эталона нет"
    else -> "только PDF, значения ещё не извлечены"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArticleRow(store: AppStore, article: ArticleSummaryDto, first: Boolean) {
    val source = remember { MutableInteractionSource() }
    val hover = hovered(source)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (hover) C.surface2 else C.surface)
            .topDivider(!first, C.divider)
            .hoverable(source)
            .clickable(source, indication = null) { store.openArticle(article.id) }
            .hand()
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(8.dp)) { if (store.isUnseen(article.id)) NewDot("Новая, ещё не открыта") }
        Column(Modifier.weight(1f)) {
            Text(article.title ?: article.id, style = T.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(article.meta(), style = T.small.copy(lineHeight = T.small.fontSize * 1.5f), color = C.fg2)
            article.exclusionReason?.takeIf { article.inScope == false }?.let { Text(it, style = T.small, color = C.fg2) }
        }
        FlowRow(Modifier.widthIn(max = 280.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (article.issueCount > 0) Badge(plural(article.issueCount, Issues), Tone.ERR)
            if (article.scan) Badge("Скан", Tone.WARN)
            if (article.inScope == false) Badge("Вне области")
            if (!article.processed) Badge("Не обработана", hint = "Значений пока нет: статью ещё не обработал конвейер")
        }
        GlyphIcon(Glyph.RIGHT, C.fg3)
    }
}

@Composable
private fun UploadRail(store: AppStore) {
    Block {
        Column(Modifier.padding(12.dp)) {
            Text("Загрузка статей", style = T.h2)
            Text(
                "PDF, можно несколько файлов сразу. Статьи попадают в набор «загруженные» выбранной области.",
                style = T.small,
                color = C.fg2,
            )
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Область", style = T.body, color = C.fg2)
                ProfileSelect(store, Modifier.weight(1f))
            }
            DropZone(store)
        }
        UploadQueue(store)
    }
}

@Composable
private fun ProfileSelect(store: AppStore, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val selected = store.profiles.firstOrNull { it.name == (store.uploadProfile ?: store.profile?.name) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(R8).background(C.surface).border(1.dp, C.border, R8).press { open = true }.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected?.title ?: "—", Modifier.weight(1f), style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            GlyphIcon(Glyph.DOWN, C.fg2)
        }
        DropdownMenu(open, { open = false }, Modifier.background(C.surface)) {
            store.profiles.forEach { profile ->
                val checked = profile.name == selected?.name
                Text(
                    profile.title,
                    Modifier
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                        .widthIn(min = 240.dp)
                        .clip(R6)
                        .background(if (checked) C.primary1 else Color.Transparent)
                        .press {
                            store.uploadProfile = profile.name
                            open = false
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    style = T.body,
                    color = if (checked) C.primaryInk else C.fg,
                )
            }
        }
    }
}

@Composable
private fun DropZone(store: AppStore) {
    val over = store.dragging
    Column(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(R8)
            .background(if (over) C.primary1 else C.surface2)
            .dashedBorder(if (over) C.primary else C.border, 8.dp)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlyphIcon(Glyph.UPLOAD, C.primary, size = 28.dp)
        Text("Перетащите PDF сюда", style = T.body)
        PrimaryButton("Выбрать файлы", store::pickArticles, glyph = Glyph.PLUS)
    }
}

@Composable
private fun UploadQueue(store: AppStore) {
    if (store.uploads.isEmpty()) return
    val active = store.activeUploads
    val finished = store.uploads.filter { it.stage != UploadStage.UPLOADING }
    Row(
        Modifier.fillMaxWidth().topDivider(true, C.divider, 0.dp).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Очередь", style = T.bodyMedium)
        Text(if (active.isEmpty()) "всё готово" else "в работе ${active.size}", style = T.small, color = C.fg2, modifier = Modifier.weight(1f))
        if (finished.isNotEmpty()) LinkButton("Убрать готовые", { store.removeUploads(finished.map { it.id }.toSet()) }, small = true)
    }
    store.uploads.forEach { UploadRow(store, it) }
}

@Composable
private fun UploadRow(store: AppStore, item: UploadItem) {
    Column(Modifier.fillMaxWidth().topDivider(true, C.divider, 0.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlyphIcon(Glyph.FILE, C.primary)
            Text(item.file.name, Modifier.weight(1f), style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sizeText(item.file.size), style = T.small, color = C.fg2)
            if (item.stage != UploadStage.UPLOADING) GhostButton(Glyph.CLOSE, "Убрать из списка — статья останется в наборе", { store.removeUploads(setOf(item.id)) })
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text("Загрузка файла", Modifier.weight(1f), style = T.small, color = C.fg2)
                Text(
                    if (item.stage == UploadStage.UPLOADING) "${sizeText(item.sent)} из ${sizeText(item.total)}" else "загружен",
                    style = T.smallMedium,
                )
            }
            ProgressLine(item.sent.toFloat() / item.total, ok = item.stage != UploadStage.UPLOADING)
        }
        when (item.stage) {
            UploadStage.DONE -> item.result?.let { result ->
                Alert(Tone.OK, Glyph.CHECK) {
                    Text("Статья ${result.added.joinToString()} в наборе «загруженные». Значения появятся после обработки конвейером.", style = T.body)
                    result.added.firstOrNull()?.let { id -> LinkButton("Открыть статью", { store.openArticle(id, result.ref) }) }
                }
            }
            UploadStage.FAILED -> Alert(Tone.ERR, Glyph.WARN) { Text("Не загружено: ${item.problem.orEmpty()}", style = T.body) }
            UploadStage.UPLOADING -> Unit
        }
    }
}
