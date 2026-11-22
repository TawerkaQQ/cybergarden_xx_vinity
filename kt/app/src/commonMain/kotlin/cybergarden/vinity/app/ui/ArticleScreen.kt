package cybergarden.vinity.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cybergarden.vinity.api.ArticleDto
import cybergarden.vinity.api.ComparisonDto
import cybergarden.vinity.api.IssueReviewStateDto
import cybergarden.vinity.api.ProcessingStateDto
import cybergarden.vinity.api.ProfileDto
import cybergarden.vinity.api.ValueDto
import cybergarden.vinity.api.ValueKindDto
import cybergarden.vinity.app.AppStore
import cybergarden.vinity.app.ArticleTab
import cybergarden.vinity.app.GroupBy
import cybergarden.vinity.app.ViewerTarget

@Composable
fun ArticleScreen(store: AppStore) {
    val id = store.articleId ?: return
    val article = store.article
    if (article == null) {
        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { Spinner(size = 24.dp) }
        return
    }
    val comparison = store.comparison
    ScreenList("article-${store.ref?.path}-$id-${store.articleTab}") {
        centered("back") { Box(Modifier.padding(bottom = 12.dp)) { LinkButton("Все статьи", store::goHome, glyph = Glyph.LEFT) } }
        centered("header") { ArticleHeader(store, article) }
        if (article.inScope != false && article.processed) centered("stats") { ArticleStats(store, article) }
        centered("tabs") { ArticleTabs(store, article) }
        when (store.articleTab) {
            ArticleTab.VALUES -> valuesTab(store, article, comparison)
            ArticleTab.ISSUES -> issuesTab(store, article, comparison)
            ArticleTab.RECORDS -> recordsTab(article)
            ArticleTab.INFO -> centered("info") { InfoTab(store, article) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArticleHeader(store: AppStore, article: ArticleDto) {
    val active = store.activeIssues(article).size
    val authors = authorsList(article.authors)
    Block {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 4.dp)) {
            Text(article.title ?: article.id, style = T.h1)
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    !article.processed || article.processing.shown() -> ProcessingBadge(article.processing, article.processed)
                    article.inScope == false -> Badge("Вне области")
                    else -> Badge("В области", Tone.OK)
                }
                if (active > 0) Badge(plural(active, Issues), Tone.ERR) else if (article.processed && article.inScope != false) Badge("Замечаний нет", Tone.OK)
                if (article.pagesWithoutText.isNotEmpty()) Badge("Скан, текст распознан", Tone.WARN, hint = "Страницы без текстового слоя: ${article.pagesWithoutText.joinToString()}")
            }
        }
        FieldRow("ID и год", listOfNotNull(article.id, article.year, article.pageCount?.let { plural(it, Pages) }).joinToString(" · "), first = true)
        if (authors.isNotEmpty()) {
            FieldRow("Авторы", first = false) {
                val short = authors.size > 3 && !store.authorsOpen
                Text(
                    if (short) authors.take(3).joinToString(", ") + " и ещё ${authors.size - 3}" else authors.joinToString(", "),
                    style = T.body,
                    textAlign = TextAlign.End,
                )
                if (authors.size > 3) LinkButton(if (store.authorsOpen) "Свернуть" else "Раскрыть", { store.authorsOpen = !store.authorsOpen })
            }
        }
        FieldRow("DOI", first = false) { Text(article.doi ?: "—", style = T.mono, textAlign = TextAlign.End) }
        if (article.overlaps.isNotEmpty()) {
            FieldRow("Те же результаты", first = false, info = "У статей совпадают показатель, число и единица хотя бы у трёх значений. Обычно одна статья приводит результаты другой.") {
                FlowRow( horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    article.overlaps.forEach { overlap ->
                        Chip("${overlap.publicationId} · ${plural(overlap.values, Values)}", pressed = false, onClick = { store.openArticle(overlap.publicationId) }, hint = store.set?.articles?.firstOrNull { it.id == overlap.publicationId }?.title)
                    }
                }
            }
        }
        if (article.pageCount != null) FieldRow("PDF", first = false) { LinkButton("Листать страницы", { openPdf(store, article) }, glyph = Glyph.EXPAND) }
        article.exclusionReason?.takeIf { article.inScope == false }?.let { FieldRow("Почему вне области", it, first = false) }
    }
}

@Composable
private fun ArticleStats(store: AppStore, article: ArticleDto) {
    val values = article.values
    StatsLine(
        listOf(
            Stat("Значений", values.size.toString(), C.fg),
            Stat("Из таблиц", values.count { it.kind == ValueKindDto.TABLE }.toString(), C.fg),
            Stat("Из текста", values.count { it.kind == ValueKindDto.TEXT }.toString(), C.fg),
            Stat("С рисунков", values.count { it.kind == ValueKindDto.FIGURE }.toString(), C.fg),
            Stat("Повторов", values.count { it.duplicateOf != null }.toString(), C.fg),
            Stat("Замечаний", store.activeIssues(article).size.toString(), C.errInk),
            Stat("Проверено", "${values.count { it.review != null }} из ${values.size}", C.fg),
        ),
        Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun ArticleTabs(store: AppStore, article: ArticleDto) {
    val profile = store.profile
    val tabs = listOf(
        TabSpec(ArticleTab.VALUES, "Значения", article.values.size),
        TabSpec(ArticleTab.ISSUES, "Замечания", store.activeIssues(article).size),
        TabSpec(ArticleTab.RECORDS, profile?.recordsTab ?: "Записи", article.records.sumOf { it.records.size }),
        TabSpec(ArticleTab.INFO, "Сведения"),
    )
    Column(Modifier.padding(top = 16.dp)) {
        UnderlineTabs(tabs, store.articleTab, store::openArticleTab, Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.divider))
    }
}

@Composable
private fun UnprocessedBlock(store: AppStore, article: ArticleDto) {
    val processing = article.processing
    val pipeline = store.set?.pipeline
    val canProcess = pipeline?.available == true
    Box(Modifier.padding(top = 12.dp)) {
        when {
            processing?.active == true -> Alert(Tone.INFO, Glyph.INFO) {
                Text(if (processing.state == ProcessingStateDto.QUEUED) "Статья ждёт очереди конвейера" else "Статью обрабатывает конвейер", style = T.alert)
                val details = listOfNotNull(processing.step, processing.seconds?.let { "идёт ${durationText(it)}" }, pipeline?.model?.let { "модель $it" })
                if (details.isNotEmpty()) Text(details.joinToString(" · "), style = T.body, color = C.fg2)
                Text("Значения появятся здесь сами, когда обработка закончится. Страницы PDF уже можно листать.", style = T.body)
                Row(Modifier.padding(top = 8.dp)) { DefaultButton("Открыть PDF", { openPdf(store, article) }, glyph = Glyph.EXPAND) }
            }
            processing?.state == ProcessingStateDto.FAILED -> Alert(Tone.ERR, Glyph.WARN) {
                Text("Конвейер не смог обработать статью", style = T.alert)
                processing.problem?.let { Text(it, style = T.body) }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canProcess) PrimaryButton("Обработать заново", { store.processArticle(article.id) })
                    DefaultButton("Открыть PDF", { openPdf(store, article) }, glyph = Glyph.EXPAND)
                }
            }
            else -> Alert(Tone.INFO, Glyph.INFO) {
                Text("Значения ещё не извлечены", style = T.alert)
                Text("У статьи нет ни результата системы, ни эталона: её ещё не обработал конвейер. Страницы PDF уже можно листать.", style = T.body)
                if (!canProcess) pipeline?.problem?.let { Text("Обработка недоступна: $it", style = T.body, color = C.fg2) }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canProcess) PrimaryButton("Обработать конвейером", { store.processArticle(article.id) })
                    DefaultButton("Открыть PDF", { openPdf(store, article) }, glyph = Glyph.EXPAND)
                }
            }
        }
    }
}

private class ValueGroup(val key: String, val title: String, val hint: String?, val values: List<ValueDto>, val missed: List<ValueDto>)

private fun LazyListScope.valuesTab(store: AppStore, article: ArticleDto, comparison: ComparisonDto?) {
    if (!article.processed) {
        centered("unprocessed") { UnprocessedBlock(store, article) }
        return
    }
    if (article.inScope == false) {
        centered("out") { Box(Modifier.padding(top = 12.dp)) { EmptyState("Статья вне области, значения из неё не извлекаются.") } }
        return
    }
    centered("toolbar") { ValuesToolbar(store, comparison) }
    val missed = if (store.onlyIssues) emptyList() else comparison?.missed.orEmpty()
    val extra = comparison?.extra.orEmpty().toSet()
    val shown = article.values.filter { value ->
        (store.showDuplicates || value.duplicateOf == null) && (!store.onlyIssues || store.activeIssueCount(value) > 0)
    }
    val groups = group(store.profile, article, shown, missed, store.groupBy)
    if (groups.isEmpty()) {
        centered("none") { EmptyState("Под эти условия значений нет.") }
        return
    }
    groups.forEach { group ->
        centered("h-${group.key}") {
            SectionHeader(group.title, group.hint) { Text(plural(group.values.size, Values), style = T.small, color = C.fg2) }
        }
        val rows: List<Pair<ValueDto, Boolean>> = group.values.map { it to false } + group.missed.map { it to true }
        rows.forEachIndexed { index, (value, ghost) ->
            item("v-${group.key}-${value.id}-$ghost") {
                Centered {
                    BlockPiece(first = index == 0, last = index == rows.lastIndex) {
                        if (ghost) {
                            MissedValueRow(store.profile, value, index == 0, showRecord = store.groupBy == GroupBy.METRIC)
                        } else {
                            ValueRow(store, value, index == 0, showRecord = store.groupBy == GroupBy.METRIC, extra = value.id in extra)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ValuesToolbar(store: AppStore, comparison: ComparisonDto?) {
    val article = store.article ?: return
    Column {
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val center = Modifier.align(Alignment.CenterVertically)
            Segmented(
                listOf(GroupBy.RECORD to (store.profile?.groupByRecord ?: "По записям"), GroupBy.METRIC to "По показателям"),
                store.groupBy,
                { store.groupBy = it },
                center,
            )
            Row(center, verticalAlignment = Alignment.CenterVertically) {
                Toggle("Показывать повторы", store.showDuplicates, { store.showDuplicates = it })
                InfoIcon("Повтор — то же число, найденное в другом месте статьи. Основным считается упоминание из таблицы, затем с рисунка.")
            }
            Toggle("Только с замечаниями", store.onlyIssues, { store.onlyIssues = it }, center)
            if (store.canCompare) Toggle("Сравнить с экспертами", store.compare, store::toggleCompare, center)
        }
        if (comparison != null) {
            val extra = comparison.extra.toSet()
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(R8).background(C.surface).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CompareStat("Совпало с экспертами", article.values.count { it.id !in extra }.toString(), C.fg)
                CompareStat("Лишних", extra.size.toString(), C.warnInk)
                CompareStat("Пропущено системой", comparison.missed.size.toString(), C.fg)
            }
        }
    }
}

@Composable
private fun CompareStat(label: String, value: String, color: Color) {
    Row {
        Text("$label: ", style = T.small)
        Text(value, style = T.smallMedium, color = color)
    }
}

private fun group(profile: ProfileDto?, article: ArticleDto, values: List<ValueDto>, missed: List<ValueDto>, by: GroupBy): List<ValueGroup> {
    val key: (ValueDto) -> String = if (by == GroupBy.RECORD) { v -> v.record ?: "" } else { v -> v.metric ?: "" }
    val order = (values + missed).map(key).distinct()
    val records = article.records.flatMap { table -> table.records.map { it.id to it } }.toMap()
    return order.map { k ->
        val own = values.filter { key(it) == k }
        val ghosts = missed.filter { key(it) == k }
        val sample = (own + ghosts).first()
        if (by == GroupBy.RECORD) {
            val record = records[k]
            ValueGroup(
                key = "r-$k",
                title = sample.recordLabel ?: record?.label ?: k.ifEmpty { "Без записи" },
                hint = record?.fields?.filter { it.value != record.label }?.take(2)?.joinToString(" · ") { it.value },
                values = own,
                missed = ghosts,
            )
        } else {
            ValueGroup(
                key = "m-$k",
                title = listOf(profile.metricTitle(sample.metric), sample.metric).distinct().filterNotNull().joinToString(" · "),
                hint = profile.metricExplain(sample.metric),
                values = own,
                missed = ghosts,
            )
        }
    }.filter { it.values.isNotEmpty() || it.missed.isNotEmpty() }
}

private fun LazyListScope.issuesTab(store: AppStore, article: ArticleDto, comparison: ComparisonDto?) {
    val issues = article.issues.sortedBy { it.review?.state == IssueReviewStateDto.DISMISSED }
    val missed = comparison?.missedIssues.orEmpty()
    val matched = comparison?.matchedIssues?.toSet()
    if (issues.isEmpty()) {
        centered("ok") {
            Box(Modifier.padding(top = 12.dp)) {
                Alert(Tone.OK, Glyph.CHECK) {
                    Text("Замечаний нет", style = T.alert)
                    Text(if (article.inScope == false) "Статья вне области, проверки не запускались." else "Все проверки согласованности пройдены.", style = T.body)
                }
            }
        }
    }
    issues.forEach { issue ->
        centered("i-${issue.id}") {
            Box(Modifier.padding(top = 12.dp)) { IssueCard(store, issue, gold = matched?.let { issue.id in it }) }
        }
    }
    missed.forEach { issue ->
        centered("mi-${issue.id}") { Box(Modifier.padding(top = 12.dp)) { MissedIssueCard(store.profile, issue) } }
    }
}

private fun LazyListScope.recordsTab(article: ArticleDto) {
    val records = article.records.flatMap { table -> table.records.map { table.table to it } }
    if (records.isEmpty()) {
        centered("none") { Box(Modifier.padding(top = 12.dp)) { EmptyState("Записей нет.") } }
        return
    }
    records.forEach { (table, record) ->
        val count = article.values.count { it.record == record.id }
        centered("rec-$table-${record.id}") {
            Column {
                SectionHeader(record.label, "$table · ${record.id}") {
                    if (count > 0) Text(plural(count, Values), style = T.small, color = C.fg2)
                }
                Block {
                    if (record.fields.isEmpty()) {
                        FieldRow("Подробностей в статье нет", "", first = true)
                    }
                    record.fields.forEachIndexed { index, field ->
                        FieldRow(field.name.replace('_', ' '), field.value, first = index == 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoTab(store: AppStore, article: ArticleDto) {
    val set = store.set
    val profile = store.profile
    val rows = listOfNotNull(
        "ID" to article.id,
        "Название" to (article.title ?: "—"),
        "Год" to (article.year ?: "—"),
        "DOI" to (article.doi ?: "—"),
        "Авторы" to authorsList(article.authors).joinToString(", ").ifEmpty { "—" },
        "Область" to if (article.inScope == false) "вне области" else (profile?.title ?: "—"),
        article.exclusionReason?.takeIf { article.inScope == false }?.let { "Почему вне области" to it },
        "Страниц" to (article.pageCount?.toString() ?: "—"),
        "Текст страниц" to textSource(article),
        "Набор" to (set?.ref?.path ?: "—"),
        "Значения" to (set?.source?.title() ?: "не извлечены"),
        "Профиль извлечения" to (profile?.name ?: "—"),
    )
    Block(Modifier.padding(top = 12.dp)) {
        rows.forEachIndexed { index, (label, value) -> FieldRow(label, value, first = index == 0) }
    }
}

private fun openPdf(store: AppStore, article: ArticleDto) {
    store.openFullscreen(ViewerTarget("pdf-${article.id}", article.id, 1, article.pageCount, emptyMap()))
}

private fun textSource(article: ArticleDto): String {
    val variants = article.textVariants.map(::variantTitle)
    val layer = if (variants.isEmpty()) "текст страниц не получен" else "варианты текста: ${variants.joinToString(", ")}"
    return if (article.pagesWithoutText.isEmpty()) layer else "$layer; без текстового слоя: стр. ${article.pagesWithoutText.joinToString()}"
}
