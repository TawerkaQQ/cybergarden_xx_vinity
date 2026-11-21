package cybergarden.vinity.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cybergarden.vinity.api.ReviewFilterDto
import cybergarden.vinity.api.ValueDto
import cybergarden.vinity.api.ValueKindDto
import cybergarden.vinity.api.ValueReviewStateDto
import cybergarden.vinity.api.ValueSortDto
import cybergarden.vinity.app.AppStore
import cybergarden.vinity.app.Evidence
import cybergarden.vinity.app.ValueFilter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ValuesScreen(store: AppStore) {
    val set = store.set ?: return
    val table = store.valueTable
    val state = remember(set.ref, set.source) { LazyListState() }
    val compact = LocalWidth.current == Width.PHONE || LocalWidth.current == Width.TABLET
    val titles = remember(set) { set.articles.associate { it.id to it.title } }
    LaunchedEffect(state, store.valueRows.size) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= state.layoutInfo.totalItemsCount - 12) store.loadMoreValues() }
    }
    ScreenList("values-${set.ref.path}-${set.source}", state = state) {
        centered("head") {
            PageHeader("Все значения", "Поиск и сравнение по всему набору · ${set.profile.title} · ${setTitle(set.ref, set.uploads)}") {
                DefaultButton("Скачать CSV", store::exportCsv, glyph = Glyph.DOWNLOAD)
            }
        }
        if (table != null && table.setTotal == 0) {
            centered("empty") { EmptyState("Значений пока нет. Загрузите статьи на экране «Статьи».") }
            return@ScreenList
        }
        centered("filters") { Filters(store) }
        centered("found") {
            SectionHeader(
                if (table == null) "Загрузка…" else "Найдено ${plural(table.total, Values)}",
                table?.let { "из ${it.setTotal} в наборе. Нажмите на строку, чтобы открыть цитату." },
            )
        }
        table?.histogram?.let { histogram ->
            centered("histogram") {
                Block(Modifier.padding(bottom = 12.dp)) {
                    Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Распределение: ${store.profile.metricTitle(histogram.metric)}" + (unitText(histogram.unit).takeIf { it.isNotEmpty() }?.let { ", $it" } ?: ""),
                                style = T.bodyMedium,
                            )
                            Text("Число значений в каждом интервале. Нажмите на столбец, чтобы оставить только его.", style = T.small, color = C.fg2)
                        }
                        Histogram(
                            histogram,
                            store.valueFilter.min.replace(',', '.').toDoubleOrNull(),
                            store.valueFilter.max.replace(',', '.').toDoubleOrNull(),
                            { bin -> store.updateFilter(store.valueFilter.copy(min = number(bin.from), max = number(bin.to))) },
                        )
                    }
                }
            }
        }
        val rows = store.valueRows
        if (table != null && rows.isEmpty() && !store.valuesLoading) {
            centered("none") { EmptyState("Под эти фильтры значений нет.") }
        }
        if (rows.isNotEmpty() && !compact) {
            item("table-head") { Centered { BlockPiece(first = true, last = false) { TableHead(store) } } }
        }
        rows.forEachIndexed { index, value ->
            item("row-${value.publicationId}-${value.id}") {
                Centered {
                    BlockPiece(first = compact && index == 0, last = index == rows.lastIndex && rows.size >= store.valueTotal) {
                        if (compact) ValueCard(store, value, titles[value.publicationId], index == 0) else TableRow(store, value, titles[value.publicationId])
                    }
                }
            }
        }
        if (store.valuesLoading) {
            centered("loading") { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Spinner(size = 20.dp) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filters(store: AppStore) {
    val filter = store.valueFilter
    val table = store.valueTable
    val single = filter.metrics.size == 1
    var allMetrics by remember { mutableStateOf(false) }
    Block {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterRow("Поиск") {
                InputBox(filter.query, { store.updateFilter(filter.copy(query = it), typed = true) }, "Статья, запись, число или текст цитаты", Modifier.widthIn(min = 260.dp, max = 440.dp))
            }
            val metrics = table?.metrics.orEmpty()
            if (metrics.isNotEmpty()) {
                FilterRow("Показатель") {
                    val shown = if (allMetrics) metrics else metrics.take(METRIC_CHIPS)
                    (shown.map { it.name } + filter.metrics.filter { selected -> shown.none { it.name == selected } }).forEach { metric ->
                        Chip(metric, metric in filter.metrics, { store.updateFilter(filter.copy(metrics = filter.metrics.toggle(metric))) }, hint = store.profile.metricTitle(metric).takeIf { it != metric })
                    }
                    if (metrics.size > METRIC_CHIPS) LinkButton(if (allMetrics) "Свернуть" else "Все показатели (${metrics.size})", { allMetrics = !allMetrics }, small = true)
                }
            }
            FilterRow("Откуда") {
                ValueKindDto.entries.forEach { kind ->
                    Chip(kind.title(), kind in filter.kinds, { store.updateFilter(filter.copy(kinds = filter.kinds.toggle(kind))) })
                }
            }
            FilterRow("Проверка") {
                listOf(ReviewFilterDto.UNCHECKED to "Не проверено", ReviewFilterDto.CHECKED to "Проверено референтом", ReviewFilterDto.NO_QUOTE to "Цитата не найдена").forEach { (key, label) ->
                    Chip(label, key in filter.review, { store.updateFilter(filter.copy(review = filter.review.toggle(key))) })
                }
            }
            FilterRow("Диапазон") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InputBox(filter.min, { store.updateFilter(filter.copy(min = it.trim()), typed = true) }, "от", Modifier.width(96.dp), glyph = null, enabled = single, numeric = true)
                    Text("—", style = T.body)
                    InputBox(filter.max, { store.updateFilter(filter.copy(max = it.trim()), typed = true) }, "до", Modifier.width(96.dp), glyph = null, enabled = single, numeric = true)
                }
                val unit = table?.histogram?.unit
                Text(
                    if (single) unitText(unit).ifEmpty { "без единицы" } else "Выберите один показатель: появятся диапазон и распределение.",
                    style = T.small,
                    color = C.fg2,
                )
            }
            FilterRow("Показывать") {
                Toggle("Только с замечаниями", filter.onlyIssues, { store.updateFilter(filter.copy(onlyIssues = it)) })
                Toggle("Без повторов", filter.hideDuplicates, { store.updateFilter(filter.copy(hideDuplicates = it)) })
                if (filter != ValueFilter(sort = filter.sort, descending = filter.descending)) LinkButton("Сбросить фильтры", store::resetFilter)
            }
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(label: String, content: @Composable () -> Unit) {
    val phone = LocalWidth.current == Width.PHONE
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, Modifier.then(if (phone) Modifier.fillMaxWidth() else Modifier.width(92.dp)), style = T.body, color = C.fg2)
        content()
    }
}

private class TableColumn(val sort: ValueSortDto?, val title: String, val weight: Float, val end: Boolean = false)

@Composable
private fun columns(store: AppStore) = listOf(
    TableColumn(ValueSortDto.ARTICLE, "Статья", 1.5f),
    TableColumn(ValueSortDto.METRIC, "Показатель", 1.4f),
    TableColumn(ValueSortDto.VALUE, "Значение", 1.1f, end = true),
    TableColumn(null, "В статье", 1.1f),
    TableColumn(ValueSortDto.RECORD, store.profile?.recordTitle ?: "Запись", 1.4f),
    TableColumn(null, "Агрегация", 1f),
    TableColumn(ValueSortDto.PLACE, "Где", 1.1f),
    TableColumn(ValueSortDto.STATUS, "Статус", 1.3f),
)

@Composable
private fun TableHead(store: AppStore) {
    val filter = store.valueFilter
    Row(Modifier.fillMaxWidth().padding(horizontal = 0.dp), verticalAlignment = Alignment.CenterVertically) {
        columns(store).forEach { column ->
            val active = column.sort != null && filter.sort == column.sort
            val arrow = if (active) (if (filter.descending) " ↓" else " ↑") else ""
            Text(
                column.title + arrow,
                Modifier.weight(column.weight).then(if (column.sort != null) Modifier.press { store.sortValues(column.sort) } else Modifier).padding(horizontal = 12.dp, vertical = 10.dp),
                style = T.small,
                color = if (active) C.fg else C.fg2,
                textAlign = if (column.end) TextAlign.End else TextAlign.Start,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RowScope.Cell(weight: Float, end: Boolean = false, content: @Composable () -> Unit) {
    Column(Modifier.weight(weight).padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = if (end) Alignment.End else Alignment.Start) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableRow(store: AppStore, value: ValueDto, title: String?) {
    val profile = store.profile
    val selected = (store.evidence as? Evidence.OnValue)?.let { it.article == value.publicationId && it.id == value.id } == true
    val source = remember { MutableInteractionSource() }
    val hover = hovered(source)
    val widths = columns(store).map { it.weight }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) C.primary1 else if (hover) C.surface2 else C.surface)
            .topDivider(true, C.divider, 0.dp)
            .hoverable(source)
            .clickable(source, indication = null) { store.openValue(value.publicationId, value.id) }
            .hand(),
        verticalAlignment = Alignment.Top,
    ) {
        Cell(widths[0]) { ArticleLabel(value.publicationId, title) }
        Cell(widths[1]) {
            Text(profile.metricTitle(value.metric), style = T.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            value.metric?.takeIf { it != profile.metricTitle(it) }?.let { Text(it, style = T.small, color = C.fg2) }
        }
        Cell(widths[2], end = true) {
            AmountText(value)
        }
        Cell(widths[3]) { Text(value.rawValue?.let { "«${withUnit(it, value.rawUnit)}»" } ?: "—", style = T.body, color = C.fg2, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Cell(widths[4]) {
            Text(value.recordLabel ?: "—", style = T.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            value.links.filter { it.concise && it.id != value.record }.firstOrNull()?.let { Text(it.label, style = T.small, color = C.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Cell(widths[5]) { Text(profile.aggregationTitle(value.aggregation) ?: "—", style = T.body, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Cell(widths[6]) {
            Text(value.page?.let { "стр. $it" } ?: "—", style = T.body)
            Text(value.place(), style = T.small, color = C.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Cell(widths[7]) { StatusBadges(store, value) }
    }
}

@Composable
private fun AmountText(value: ValueDto) {
    val rejected = value.review?.state == ValueReviewStateDto.REJECTED
    Text(
        value.fixedAmount(),
        style = T.bodyMedium.copy(textDecoration = if (rejected) TextDecoration.LineThrough else null),
        color = if (rejected) C.fg2 else C.fg,
        textAlign = TextAlign.End,
    )
    if (value.review?.state == ValueReviewStateDto.FIXED) Text(value.amount(), style = T.small.copy(textDecoration = TextDecoration.LineThrough), color = C.fg2)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusBadges(store: AppStore, value: ValueDto) {
    val issues = store.activeIssueCount(value)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        value.review?.let { Badge(it.state.title(), it.state.tone()) }
        if (issues > 0) Badge(plural(issues, Issues), Tone.ERR)
        if (value.duplicateOf != null) Badge("Повтор")
        if (!value.quote.status.found()) Badge("Без цитаты", Tone.WARN)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ValueCard(store: AppStore, value: ValueDto, title: String?, first: Boolean) {
    val profile = store.profile
    val selected = (store.evidence as? Evidence.OnValue)?.let { it.article == value.publicationId && it.id == value.id } == true
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) C.primary1 else C.surface)
            .topDivider(!first, C.divider)
            .press { store.openValue(value.publicationId, value.id) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(profile.metricTitle(value.metric), style = T.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(value.publicationId, value.recordLabel, value.pageAndPlace()).joinToString(" · "), style = T.small, color = C.fg2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                title?.let { Text(it, style = T.small, color = C.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Column(horizontalAlignment = Alignment.End) { AmountText(value) }
        }
        StatusBadges(store, value)
    }
}

private const val METRIC_CHIPS = 24
