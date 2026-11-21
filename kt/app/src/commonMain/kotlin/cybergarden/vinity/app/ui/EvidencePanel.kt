package cybergarden.vinity.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cybergarden.vinity.api.ArticleDto
import cybergarden.vinity.api.IssueDto
import cybergarden.vinity.api.IssueReviewStateDto
import cybergarden.vinity.api.QuoteStatusDto
import cybergarden.vinity.api.ValueDto
import cybergarden.vinity.api.ValueKindDto
import cybergarden.vinity.api.ValueReviewStateDto
import cybergarden.vinity.app.AppStore
import cybergarden.vinity.app.Evidence
import cybergarden.vinity.app.Mark
import cybergarden.vinity.app.ViewerTarget

private val DismissReasons = listOf("Разные условия измерения", "Округление в статье", "Число сверено со сканом", "Другая причина")

@Composable
fun EvidencePanel(store: AppStore, modifier: Modifier, docked: Boolean) {
    val evidence = store.evidence ?: return
    var width by remember { mutableStateOf(600.dp) }
    val density = LocalDensity.current
    val divider = C.divider
    Row(modifier.then(if (docked) Modifier.width(width) else Modifier).background(C.surface).drawBehind {
        if (docked) drawLine(divider, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx())
    }) {
        if (docked) {
            val source = remember { MutableInteractionSource() }
            val hover = hovered(source)
            Box(
                Modifier.width(6.dp).fillMaxHeight().hoverable(source).background(if (hover) C.primary.copy(alpha = 0.4f) else C.surface)
                    .draggable(
                        rememberDraggableState { delta -> width = (width - with(density) { delta.toDp() }).coerceIn(380.dp, 960.dp) },
                        Orientation.Horizontal,
                    ),
            )
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val article = store.articles[evidence.article]
            when {
                article == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(size = 24.dp) }
                evidence is Evidence.OnValue -> article.values.firstOrNull { it.id == evidence.id }?.let { ValuePanel(store, article, it) } ?: Missing(store)
                evidence is Evidence.OnIssue -> article.issues.firstOrNull { it.id == evidence.id }?.let { IssuePanel(store, article, it) } ?: Missing(store)
            }
        }
    }
}

@Composable
private fun Missing(store: AppStore) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelHead(store, "Не найдено", "Значение или замечание есть в ссылке, но не в данных набора")
    }
}

@Composable
private fun PanelHead(store: AppStore, title: String, subtitle: String, code: String? = null) {
    Row(
        Modifier.fillMaxWidth().drawBottomLine(C.divider).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row {
                Text(title, style = T.h2, modifier = Modifier.weight(1f, fill = false))
                code?.let { Text(" $it", style = T.h2.copy(fontWeight = T.body.fontWeight), color = C.fg2) }
            }
            Text(subtitle, style = T.small, color = C.fg2)
        }
        GhostButton(Glyph.LINK, "Скопировать ссылку", { store.copyLink() })
        GhostButton(Glyph.CLOSE, "Закрыть панель", store::closeEvidence)
    }
}

private fun Modifier.drawBottomLine(color: Color): Modifier = drawBehind {
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
}

@Composable
private fun ColumnScope.PanelBody(key: Any, content: @Composable ColumnScope.() -> Unit) {
    val scroll = remember(key) { ScrollState(0) }
    Column(
        Modifier.weight(1f).fillMaxWidth().background(C.bg).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun ColumnScope.ValuePanel(store: AppStore, article: ArticleDto, value: ValueDto) {
    val profile = store.profile
    val issues = article.issues.filter { it.id in value.issues && it.review?.state != IssueReviewStateDto.DISMISSED }
    val subtitle = listOfNotNull(
        value.publicationId,
        value.recordLabel,
        profile.aggregationTitle(value.aggregation),
        value.links.filter { it.concise && it.id != value.record }.joinToString(" · ") { it.label }.ifEmpty { null },
    ).joinToString(" · ")
    val metricTitle = profile.metricTitle(value.metric)
    PanelHead(store, metricTitle, subtitle, value.metric?.takeIf { it != metricTitle })
    PanelBody("v-${value.publicationId}-${value.id}") {
        profile.metricExplain(value.metric)?.let { Explain(it) }
        issues.forEach { issue ->
            Alert(Tone.ERR, Glyph.WARN) {
                Text(profile.checkTitle(issue.check), style = T.alert)
                issue.description?.let { Text(it, style = T.body) }
                LinkButton("Разобрать замечание", { store.openIssue(issue.publicationId, issue.id) })
            }
        }
        Block {
            FieldRow("Значение", first = true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    value.review?.let { Badge(it.state.title(), it.state.tone()) }
                    Text(
                        value.fixedAmount(),
                        style = T.bodyMedium.copy(textDecoration = if (value.review?.state == ValueReviewStateDto.REJECTED) TextDecoration.LineThrough else null),
                    )
                }
                if (value.review?.state == ValueReviewStateDto.FIXED) Text("было ${value.amount()}", style = T.small, color = C.fg2)
            }
            FieldRow("В статье", first = false) {
                Text(value.rawValue?.let { "«${withUnit(it, value.rawUnit)}»" } ?: "—", style = T.body, textAlign = TextAlign.End)
                value.status?.takeIf { it != "extracted" }?.let { Text(statusText(it), style = T.small, color = C.fg2, textAlign = TextAlign.End) }
            }
            FieldRow("Нормализация", value.written()?.let { "«$it» → ${value.amount()}" } ?: "без изменений", first = false)
            value.note?.let { FieldRow("Примечание", it, first = false) }
            FieldRow("Где", value.pageAndPlace(), first = false)
            FieldRow("Цитата", first = false) { QuoteStatus(value) }
        }
        val page = value.page
        if (page != null) {
            val marks = value.fragment?.let { mapOf(page to listOf(Mark(it, error = issues.isNotEmpty(), focus = true))) }.orEmpty()
            Viewer(store, ViewerTarget("v:${value.publicationId}/${value.id}", article.id, page, article.pageCount, marks))
        }
        value.fragment?.let { fragment ->
            Block {
                SectionHeader("Цитата из статьи")
                Text("«${fragment.replace('\n', ' ')}»", Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), style = T.body.copy(fontStyle = FontStyle.Italic, fontSize = T.alert.fontSize * 0.94f))
            }
        }
        MoreButton(store.detailsOpen) { store.detailsOpen = !store.detailsOpen }
        if (store.detailsOpen) Details(store, article, value)
    }
    ReviewFoot(store, article, value)
}

private fun statusText(status: String): String = when (status) {
    "present_not_extractable" -> "в статье есть, но число не извлечь"
    "absent" -> "в статье явно отсутствует"
    else -> status
}


@Composable
private fun QuoteStatus(value: ValueDto) {
    val quote = value.quote
    when (quote.status) {
        QuoteStatusDto.FOUND -> Badge("найдена дословно · ${variantTitle(quote.variant)}", Tone.OK)
        QuoteStatusDto.ROW_LABEL -> {
            Badge("ссылка на строку таблицы", Tone.OK)
            Text("Для таблиц профиль допускает цитату вида «Table N, строка X».", style = T.small, color = C.fg2, textAlign = TextAlign.End)
        }
        QuoteStatusDto.ELSEWHERE -> {
            Badge("найдена на другой странице", Tone.WARN)
            Text("Есть на стр. ${quote.pages.joinToString()}, а значение указывает на стр. ${value.page ?: "—"}.", style = T.small, color = C.fg2, textAlign = TextAlign.End)
        }
        QuoteStatusDto.MISSING -> {
            Badge("не найдена в тексте страницы", Tone.WARN)
            if (value.kind == ValueKindDto.FIGURE) Text("Значение с рисунка: подписи на графике нет в текстовом слое. Для рисунков это допустимо.", style = T.small, color = C.fg2, textAlign = TextAlign.End)
        }
        QuoteStatusDto.NO_PAGE_TEXT -> {
            Badge("нет текста страницы", Tone.WARN)
            Text("Текст этой страницы не получен, проверить цитату не на чем.", style = T.small, color = C.fg2, textAlign = TextAlign.End)
        }
        QuoteStatusDto.NO_QUOTE -> Badge("цитаты нет", Tone.MISS)
    }
}

@Composable
private fun MoreButton(open: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(R8).background(C.surface).press(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (open) "Скрыть подробности" else "Подробнее: запись, агрегация, повторы", style = T.body, color = C.primary)
        GlyphIcon(Glyph.DOWN, C.primary, Modifier.rotate(if (open) 180f else 0f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(store: AppStore, article: ArticleDto, value: ValueDto) {
    val profile = store.profile
    val main = value.duplicateOf?.let { id -> article.values.firstOrNull { it.id == id } }
    val family = article.values.filter { it.duplicateOf == value.id }
    val comparison = store.comparisons[article.id]?.takeIf { store.compare }
    Block {
        FieldRow("Как найдено", value.kind.how(), first = true)
        value.links.forEach { link ->
            val title = if (link.id == value.record) profile?.recordTitle ?: link.table else link.table
            FieldRow(title, first = false) {
                Text(link.label, style = T.body, textAlign = TextAlign.End)
                val fields = link.fields.filter { it.value != link.label }.take(4)
                if (fields.isNotEmpty()) Text(fields.joinToString(" · ") { it.value }, style = T.small, color = C.fg2, textAlign = TextAlign.End)
            }
        }
        FieldRow("Агрегация", profile.aggregationTitle(value.aggregation) ?: "не указана", first = false)
        FieldRow("Происхождение", if (value.origin == "cited") "из другой работы${value.cited?.let { ": $it" } ?: ""}" else "собственный результат статьи", first = false)
        FieldRow("Повторы", first = false) {
            when {
                main != null -> {
                    Text("Это повтор. Основное упоминание:", style = T.body, textAlign = TextAlign.End)
                    Chip(main.pageAndPlace(), pressed = false, onClick = { store.openValue(main.publicationId, main.id) }, modifier = Modifier.padding(top = 4.dp))
                }
                family.isNotEmpty() -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    family.forEach { other -> Chip(other.pageAndPlace(), pressed = false, onClick = { store.openValue(other.publicationId, other.id) }) }
                }
                else -> Text("нет", style = T.body)
            }
        }
        if (comparison != null) {
            FieldRow("Эталон экспертов", first = false) {
                if (value.id in comparison.extra) Badge("у экспертов этого значения нет", Tone.WARN) else Badge("есть у экспертов")
            }
        }
    }
}

@Composable
private fun ReviewFoot(store: AppStore, article: ArticleDto, value: ValueDto) {
    val reviewed = article.values.count { it.review != null }
    Foot {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val review = value.review
            if (review != null) {
                Badge(review.state.title(), review.state.tone())
                LinkButton("Отменить", store::undoValueReview)
            } else {
                Text("Ещё не проверено", style = T.body, color = C.fg2)
            }
            Spacer(Modifier.weight(1f))
            Text("Проверено $reviewed из ${article.values.size} в статье", style = T.small, color = C.fg2)
        }
        if (store.fixing) FixRow(store, value) else {
            FlowRowCompat {
                SquareButton(Glyph.LEFT, "Предыдущее значение", { store.stepValue(-1) })
                SquareButton(Glyph.RIGHT, "Следующее значение", { store.stepValue(1) })
                Spacer(Modifier.weight(1f))
                DefaultButton("Отклонить", store::rejectValue)
                DefaultButton("Исправить", store::startFix)
                PrimaryButton("Подтвердить", store::confirmValue)
            }
        }
        if (LocalWidth.current == Width.WIDE || LocalWidth.current == Width.NARROW) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Клавиши:", style = T.small, color = C.fg2)
            Kbd("A"); Text("подтвердить ·", style = T.small, color = C.fg2)
            Kbd("E"); Text("исправить ·", style = T.small, color = C.fg2)
            Kbd("R"); Text("отклонить ·", style = T.small, color = C.fg2)
            Kbd("J"); Text("/", style = T.small, color = C.fg2); Kbd("K"); Text("соседние", style = T.small, color = C.fg2)
        }
    }
}

@Composable
private fun FixRow(store: AppStore, value: ValueDto) {
    var text by remember(value.publicationId, value.id) { mutableStateOf(value.review?.value ?: value.value.orEmpty()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(value.id) { runCatching { focus.requestFocus() } }
    FlowRowCompat {
        InputBox(text, { text = it }, "Верное значение", Modifier.width(140.dp).focusRequester(focus), glyph = null, numeric = true, onDone = { store.saveFix(text) })
        Text(unitText(value.unit), style = T.body, color = C.fg2)
        PrimaryButton("Сохранить", { store.saveFix(text) })
        DefaultButton("Отмена", store::cancelFix)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowCompat(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Foot(content: @Composable ColumnScope.() -> Unit) {
    val divider = C.divider
    Column(
        Modifier.fillMaxWidth().background(C.surface).drawBehind { drawLine(divider, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.IssuePanel(store: AppStore, article: ArticleDto, issue: IssueDto) {
    val profile = store.profile
    val values = issue.values.mapNotNull { ref -> article.values.firstOrNull { it.id == ref.id } }
    val pages = (values.mapNotNull { it.page } + listOfNotNull(issue.page)).distinct().sorted()
    val off = issue.review?.state == IssueReviewStateDto.DISMISSED
    val gold = store.comparisons[article.id]?.takeIf { store.compare }?.let { issue.id in it.matchedIssues }
    PanelHead(store, profile.checkTitle(issue.check), "${issue.publicationId} · проверка ${issue.check ?: "—"}")
    PanelBody("i-${issue.publicationId}-${issue.id}") {
        Alert(if (off) Tone.OFF else Tone.ERR, Glyph.WARN) {
            if (issue.review != null || gold != null) {
                FlowRow(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    issue.review?.let { Badge(it.state.title(), if (it.state == IssueReviewStateDto.CONFIRMED) Tone.OK else Tone.NEUTRAL) }
                    gold?.let { if (it) Badge("Есть у экспертов") else Badge("Нашли только мы", Tone.PRI) }
                }
            }
            Text(issue.description ?: "Описание не указано", style = T.body)
            issue.review?.reason?.let { Text("Причина: $it", style = T.small, color = C.fg2) }
        }
        profile.checkRule(issue.check)?.let { Explain(it, bold = "Правило.") }
        pages.forEach { page ->
            val onPage = values.filter { it.page == page }
            val marks = onPage.mapNotNull { v -> v.fragment?.let { Mark(it, error = true, focus = v == onPage.first()) } } +
                listOfNotNull(issue.fragment?.takeIf { issue.page == page }?.let { Mark(it, error = true, focus = onPage.isEmpty()) })
            Viewer(store, ViewerTarget("i:${issue.publicationId}/${issue.id}/$page", article.id, page, article.pageCount, mapOf(page to marks.distinctBy { it.fragment })))
        }
        if (values.isNotEmpty()) {
            Block {
                SectionHeader("Значения в замечании")
                values.forEachIndexed { index, value -> ValueRow(store, value, first = index == 0, showRecord = true) }
            }
        }
    }
    Foot {
        if (store.dismissing) {
            Text("Почему это не ошибка?", style = T.small, color = C.fg2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DismissReasons.forEach { reason -> Chip(reason, pressed = false, onClick = { store.dismissIssue(reason) }) }
            }
            Row { DefaultButton("Отмена", store::cancelDismiss) }
        } else {
            FlowRowCompat {
                val review = issue.review
                if (review != null) {
                    Badge(review.state.title(), if (review.state == IssueReviewStateDto.CONFIRMED) Tone.OK else Tone.NEUTRAL)
                    LinkButton("Отменить", store::undoIssueReview)
                } else {
                    Text("Ещё не разобрано", style = T.body, color = C.fg2)
                }
                Spacer(Modifier.weight(1f))
                DefaultButton("Это не ошибка", store::startDismiss)
                PrimaryButton("Подтвердить замечание", store::confirmIssue)
            }
        }
    }
}
