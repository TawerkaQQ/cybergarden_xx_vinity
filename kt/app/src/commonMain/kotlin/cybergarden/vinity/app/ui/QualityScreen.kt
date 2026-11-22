package cybergarden.vinity.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cybergarden.vinity.api.ChecksQualityDto
import cybergarden.vinity.api.CountsDto
import cybergarden.vinity.api.NormalizationDto
import cybergarden.vinity.api.ProfileDto
import cybergarden.vinity.api.QuoteQualityDto
import cybergarden.vinity.api.ScoreDto
import cybergarden.vinity.api.SourceDto
import cybergarden.vinity.api.ValueKindDto
import cybergarden.vinity.app.AppStore
import cybergarden.vinity.app.QualityTab
import cybergarden.vinity.app.TableSort

private val Ink = Color(0xFF1F1F1F)
private val Times = Word("раз", "раза", "раз")
private const val CHART_METRICS = 15
private const val TABLE_METRICS = 30
private const val UNIT_METRICS = 40
private const val METRIC_TABLE = "metrics"
private const val CHECK_TABLE = "checks"

private class TileSpec(val title: String, val value: String, val note: String?, val badge: Pair<String, Tone>? = null, val info: String? = null)

private class MetricScore(val code: String, val title: String, val counts: CountsDto) {
    val support: Int get() = counts.truePositive + counts.falseNegative
}

private val roleTitles = mapOf(
    "metric" to "показатель",
    "value" to "число",
    "origin" to "происхождение",
    "aggregation" to "агрегация",
    "page" to "страница",
    "unit" to "единица",
)

private fun QualityTab.title(): String = when (this) {
    QualityTab.EXTRACT -> "Точность и полнота"
    QualityTab.QUOTES -> "Связь с местом в статье"
    QualityTab.NORMALIZATION -> "Нормализация"
    QualityTab.CHECKS -> "Проверка достоверности"
    QualityTab.PASSPORT -> "Паспорт данных"
}

@Composable
fun QualityScreen(store: AppStore) {
    val set = store.set
    val profile = store.profile
    val quality = store.quality
    val score = store.score
    val tab = store.qualityTab
    val loading = store.loading
    val strict = store.strict
    val primaryOnly = store.primaryOnly
    val subtitle = listOfNotNull(profile?.title, set?.let { "набор ${setTitle(it.ref, it.uploads)}" }).joinToString(" · ")
    ScreenList("quality-${set?.ref?.path}-${set?.source}-$tab") {
        centered("head") { PageHeader("Качество", subtitle) }
        centered("tabs") {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Segmented(QualityTab.entries.map { it to it.title() }, tab, store::selectQualityTab)
            }
        }
        if (tab == QualityTab.PASSPORT) {
            passportTab(store, store.passport)
            return@ScreenList
        }
        if (loading && quality == null) {
            centered("loading") { ScreenLoading() }
            return@ScreenList
        }
        when (tab) {
            QualityTab.EXTRACT -> extractTab(store, score, profile, strict, primaryOnly)
            QualityTab.QUOTES -> if (quality == null) unavailable() else quotesTab(store, quality.quotes)
            QualityTab.NORMALIZATION -> if (quality == null) unavailable() else normalizationTab(store, quality.normalization, profile)
            QualityTab.CHECKS -> if (quality == null) unavailable() else checksTab(store, quality.checks, profile, set?.source)
            QualityTab.PASSPORT -> Unit
        }
    }
}

private fun LazyListScope.unavailable() {
    centered("unavailable") { EmptyState("Сводка качества для этого набора недоступна: сервер её не вернул.") }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.extractTab(store: AppStore, score: ScoreDto?, profile: ProfileDto?, strict: Boolean, primaryOnly: Boolean) {
    if (score == null) {
        centered("no-score") {
            EmptyState("Для точности и полноты нужны и результат системы, и эталон экспертов. У этого набора одного из них нет.")
        }
        return
    }
    val counts = if (primaryOnly) score.primaryRecords else score.allMentions
    val metrics = score.byMetric.entries.map { MetricScore(it.key, profile.metricTitle(it.key), it.value) }
    val charted = metrics.filter { it.support + it.counts.falsePositive > 0 }.take(CHART_METRICS).sortedByDescending { it.counts.f1 }
    val worst = charted.filter { it.support > 0 }.minByOrNull { it.counts.recall }
    val fields = score.matchedBy.joinToString(", ") { roleTitles[it] ?: it }
    val modeHint = (if (strict) "Строгое сравнение" else "Мягкое сравнение") +
        ": значение совпадает с эталоном по полям — $fields. " +
        (if (primaryOnly) "Считаем только основные упоминания, без повторов." else "Считаем каждое найденное место в статье, включая повторы.") +
        " Эталон — ${score.publicationsScored} из ${score.publicationsInGold} статей."
    centered("modes") {
        FlowRow(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val center = Modifier.align(Alignment.CenterVertically)
            Segmented(listOf(true to "Строгое сравнение", false to "Мягкое"), strict, store::selectStrict, center)
            Segmented(listOf(false to "Все упоминания", true to "Только основные"), primaryOnly, { store.primaryOnly = it }, center)
            Box(center) { InfoIcon(modeHint) }
        }
    }
    centered("tiles") {
        TileRow(
            listOf(
                TileSpec(
                    "Точность",
                    fixed2(counts.precision),
                    "${counts.truePositive} из ${counts.truePositive + counts.falsePositive} найденных есть у экспертов",
                    info = "Какая доля найденных системой значений совпала с эталоном экспертов. Остальные — лишние или находки сверх эталона.",
                ),
                TileSpec(
                    "Полнота",
                    fixed2(counts.recall),
                    "найдено ${counts.truePositive} из ${counts.truePositive + counts.falseNegative} значений экспертов",
                    info = "Какую долю значений из эталона экспертов нашла система.",
                ),
                TileSpec("F1", fixed2(counts.f1), null, info = "Общая оценка: среднее гармоническое точности и полноты. Высокая, только если высоки обе. 1 — идеально."),
            ),
        )
    }
    centered("parts") {
        Block(Modifier.padding(top = 12.dp)) {
            Column(Modifier.padding(12.dp)) {
                Bold("Из чего складываются оценки")
                SegmentBar(
                    listOf(
                        Segment("Совпало с экспертами", counts.truePositive, C.c1, Color.White),
                        Segment("Лишние: нашли, а у экспертов нет", counts.falsePositive, C.c2, Ink),
                        Segment("Пропущено: есть у экспертов, а мы не нашли", counts.falseNegative, C.c3, Color.White),
                    ),
                )
                if (score.scopeTotal > 0) Muted("Область статьи определена верно в ${score.scopeAgreed} из ${score.scopeTotal} статей.", Modifier.padding(top = 8.dp), small = true)
            }
        }
    }
    if (charted.isNotEmpty()) {
        centered("by-metric") {
            Block(Modifier.padding(top = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Bold("F1 по показателям")
                    val more = if (metrics.size > charted.size) " · показаны ${charted.size} самых частых из ${metrics.size}" else ""
                    Muted("Наведите на строку, чтобы увидеть точность и полноту$more", small = true)
                    HBarChart(
                        rows = charted.map { metric ->
                            BarRow(metric.title, metric.counts.f1, fixed2(metric.counts.f1), C.c1, metricTip(metric), null)
                        },
                        max = 1.0,
                        ticks = listOf(0.0, 0.25, 0.5, 0.75, 1.0),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    worst?.let { metric ->
                        Alert(Tone.WARN, Glyph.INFO, Modifier.padding(top = 8.dp)) {
                            Text(
                                "Чаще всего пропускаем «${metric.title}»: нашли ${metric.counts.truePositive} из ${metric.support} значений эталона (полнота ${fixed2(metric.counts.recall)}).",
                                style = T.body,
                            )
                        }
                    }
                }
            }
        }
    }
    if (metrics.isEmpty()) return
    val shown = metrics.take(TABLE_METRICS)
    val headers = listOf("Показатель", "Совпало", "Лишних", "Пропущено", "Точность", "Полнота", "F1")
    centered("table-head") {
        SectionHeader("Таблица по показателям", if (metrics.size > shown.size) "Показаны ${shown.size} самых частых из ${metrics.size}" else null)
    }
    val lines = shown.map { metric ->
        with(metric.counts) {
            TableLine(
                title = metric.title,
                code = metric.code.takeIf { it != metric.title },
                numbers = listOf(truePositive.toString(), falsePositive.toString(), falseNegative.toString(), fixed2(precision), fixed2(recall), fixed2(f1)),
                keys = listOf(truePositive.toDouble(), falsePositive.toDouble(), falseNegative.toDouble(), precision, recall, f1),
            )
        }
    }.sortedBy(store.tableSorts[METRIC_TABLE])
    centered("table-columns") { BlockPiece(first = true, last = false) { TableHeader(headers, store.tableSorts[METRIC_TABLE]) { store.sortTable(METRIC_TABLE, it) } } }
    lines.forEachIndexed { index, line ->
        centered("table-row-$index") {
            BlockPiece(first = false, last = index == lines.lastIndex) {
                TableRow(headers = headers, title = line.title, code = line.code, numbers = line.numbers, emphasizeLast = true)
            }
        }
    }
}

private fun metricTip(metric: MetricScore): String = with(metric.counts) {
    "${metric.title}\nТочность ${fixed2(precision)} · Полнота ${fixed2(recall)} · F1 ${fixed2(f1)}\nСовпало $truePositive · лишних $falsePositive · пропущено $falseNegative"
}


private fun LazyListScope.quotesTab(store: AppStore, quotes: QuoteQualityDto) {
    if (quotes.values == 0) {
        centered("q-empty") { EmptyState("Значений пока нет.") }
        return
    }
    val without = quotes.values - quotes.found
    val goal = quotes.textValues > 0 && quotes.textFound >= quotes.textValues * 0.95
    val onlyFigures = quotes.missing.size == without && quotes.missing.all { it.kind == ValueKindDto.FIGURE }
    val withoutNote = when {
        without == 0 -> "у всех значений цитата нашлась"
        onlyFigures -> "все — подписи на рисунках: их нет в текстовом слое, критерий это допускает"
        else -> "есть значения не с рисунков — их надо разобрать"
    } + if (quotes.withoutPageText > 0) ". Без текста страницы: ${quotes.withoutPageText}" else ""
    centered("q-tiles") {
        TileRow(
            listOf(
                TileSpec(
                    "Цитата найдена: таблицы и текст",
                    percent(quotes.textFound, quotes.textValues),
                    "${quotes.textFound} из ${quotes.textValues} значений",
                    if (quotes.textValues == 0) null else if (goal) "цель 95 % достигнута" to Tone.OK else "ниже цели 95 %" to Tone.ERR,
                    info = "Цитата ищется дословно в тексте страницы, как у организаторов. Значения с рисунков здесь не считаются: подписей на графиках часто нет в текстовом слое.",
                ),
                TileSpec(
                    "Цитата найдена: все значения",
                    percent(quotes.found, quotes.values),
                    "${quotes.found} из ${quotes.values} значений",
                    info = "То же, но вместе со значениями с рисунков.",
                ),
                TileSpec("Без цитаты", without.toString(), withoutNote, info = "Значения, для которых цитату не нашли на странице. Их стоит проверить глазами."),
            ),
        )
    }
    centered("q-variants") {
        val palette = listOf(C.c1 to Color.White, C.c2 to Ink, C.c3 to Color.White, C.ok to Color.White, C.warn to Ink)
        val parts = quotes.byVariant.mapIndexed { index, count ->
            val (color, ink) = palette[index % palette.size]
            Segment(variantTitle(count.name), count.count, color, ink)
        } + Segment("не найдена", without, C.fg3, Ink)
        Block(Modifier.padding(top = 12.dp)) {
            Column(Modifier.padding(12.dp)) {
                Bold("Где нашлась цитата")
                SegmentBar(parts)
            }
        }
    }
    if (quotes.missing.isEmpty()) return
    centered("q-missing-head") {
        val more = if (without > quotes.missing.size) " · показаны первые ${quotes.missing.size} из $without" else ""
        SectionHeader("Значения без цитаты", "Нажмите, чтобы открыть страницу и проверить глазами$more")
    }
    blockItems(quotes.missing, key = { "q-${it.publicationId}-${it.id}" }) { value, first ->
        ValueRow(store, value, first, showRecord = true)
    }
}

private fun LazyListScope.normalizationTab(store: AppStore, normalization: NormalizationDto, profile: ProfileDto?) {
    if (normalization.values == 0) {
        centered("n-empty") { EmptyState("Значений пока нет.") }
        return
    }
    val complete = normalization.metrics.count { it.values > 0 && it.inUnit == it.values }
    centered("n-tiles") {
        TileRow(
            listOf(
                TileSpec(
                    "Показатели в единицах области",
                    "$complete из ${normalization.metrics.size}",
                    null,
                    info = "Сколько показателей набора целиком записаны в единицах, принятых в профиле области.",
                ),
                TileSpec(
                    "Пересчитано значений",
                    normalization.converted.toString(),
                    "остальные ${normalization.values - normalization.converted} уже были записаны как нужно",
                    info = "Значения, у которых система поменяла единицу или запись числа: например, .204 → 0.204 или секунды → миллисекунды.",
                ),
                TileSpec(
                    "Поля со списком вариантов",
                    percent(normalization.vocabularyValid, normalization.vocabularyChecked),
                    if (normalization.vocabularyChecked == 0) "таких полей в данных нет" else "${normalization.vocabularyValid} из ${normalization.vocabularyChecked} — из допустимых",
                    info = "Поля, для которых профиль задаёт список допустимых значений, например «в области / вне области». Показывает, сколько записанных значений попало в этот список.",
                ),
            ),
        )
    }
    if (normalization.conversions.isNotEmpty()) {
        centered("n-conversions-head") { SectionHeader("Преобразования", "Нажмите, чтобы открыть пример") }
        blockItems(normalization.conversions.withIndex().toList(), key = { "n-conversion-${it.index}" }) { (_, conversion), first ->
            Row(
                Modifier.fillMaxWidth().topDivider(!first, C.divider).press { store.openValue(conversion.publicationId, conversion.example.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${unitText(conversion.from).ifEmpty { "без единицы" }} → ${unitText(conversion.to).ifEmpty { "без единицы" }}", style = T.body)
                    Muted("например ${conversion.publicationId}: «${conversion.exampleRaw}» → ${withUnit(conversion.example.value ?: "—", conversion.example.unit)}", Modifier.subline(), small = true)
                }
                Text(plural(conversion.count, Values), style = T.body, textAlign = TextAlign.End)
                GlyphIcon(Glyph.RIGHT, C.fg3)
            }
        }
    }
    if (normalization.metrics.isNotEmpty()) {
        val shown = normalization.metrics.take(UNIT_METRICS)
        centered("n-units-head") {
            SectionHeader("Единицы показателей", if (normalization.metrics.size > shown.size) "Показаны ${shown.size} из ${normalization.metrics.size}" else null)
        }
        blockItems(shown.withIndex().toList(), key = { "n-unit-${it.index}" }) { (_, metric), first ->
            val title = profile.metricTitle(metric.metric)
            Row(
                Modifier.fillMaxWidth().topDivider(!first, C.divider).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = T.body)
                    if (title != metric.metric) Muted(metric.metric, Modifier.subline(), small = true)
                }
                Text(unitText(metric.unit).ifEmpty { "без единицы" }, style = T.body)
                if (metric.inUnit == metric.values) Badge("все значения", Tone.OK) else Badge("${metric.inUnit} из ${metric.values}", Tone.WARN)
            }
        }
    }
    if (normalization.aliases.isNotEmpty()) {
        centered("n-aliases-head") { SectionHeader("Синонимы единиц из профиля", "Как разные авторы пишут одну и ту же единицу") }
        blockItems(normalization.aliases.withIndex().toList(), key = { "n-alias-${it.index}" }) { (_, alias), first ->
            Row(Modifier.fillMaxWidth().topDivider(!first, C.divider).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(alias.written, Modifier.weight(1f), style = T.body, color = C.fg2)
                Text("→ ", style = T.body)
                Bold(unitText(alias.canonical).ifEmpty { alias.canonical })
                Muted(" · ${plural(alias.count, Times)}", small = true)
            }
        }
    }
}

private fun LazyListScope.checksTab(store: AppStore, checks: ChecksQualityDto, profile: ProfileDto?, source: SourceDto?) {
    val gold = checks.gold
    val reviewed = checks.confirmed + checks.dismissed
    centered("c-tiles") {
        TileRow(
            buildList {
                if (gold != null) {
                    add(TileSpec("Совпало с экспертами", "${gold.matched} из ${gold.goldTotal}", null, info = "Сколько замечаний из эталона экспертов система нашла сама."))
                    add(TileSpec("Нашли только мы", gold.onlyOurs.size.toString(), null, info = "Замечания системы, которых нет у экспертов: новая находка или ложная тревога. Решает референт."))
                } else {
                    val note = if (source == SourceDto.GOLD) "замечания экспертов из эталона" else "эталона нет, сравнивать не с чем"
                    add(TileSpec("Замечаний", checks.issues.toString(), note))
                }
                add(TileSpec("Разобрано референтом", "$reviewed из ${checks.issues}", "подтверждено ${checks.confirmed}, снято ${checks.dismissed}"))
            },
        )
    }
    if (checks.byCheck.isEmpty()) {
        centered("c-none") {
            Alert(Tone.OK, Glyph.CHECK, Modifier.padding(top = 12.dp)) {
                Text("Замечаний нет", style = T.alert)
                Text("Проверки согласованности не нашли расхождений.", style = T.body)
            }
        }
        return
    }
    val headers = if (gold != null) listOf("Правило", "Нашли", "У экспертов", "Совпало") else listOf("Правило", "Нашли")
    centered("c-table-head") { SectionHeader("По правилам") }
    val lines = checks.byCheck.map { check ->
        val counts = if (gold != null) listOf(check.found, check.gold ?: 0, check.matched ?: 0) else listOf(check.found)
        TableLine(profile.checkTitle(check.check), check.check, counts.map { it.toString() }, counts.map { it.toDouble() })
    }.sortedBy(store.tableSorts[CHECK_TABLE])
    centered("c-table-columns") { BlockPiece(first = true, last = false) { TableHeader(headers, store.tableSorts[CHECK_TABLE]) { store.sortTable(CHECK_TABLE, it) } } }
    lines.forEachIndexed { index, line ->
        centered("c-check-$index") {
            BlockPiece(first = false, last = index == lines.lastIndex) {
                TableRow(headers = headers, title = line.title, code = line.code, numbers = line.numbers, emphasizeLast = false, monoCode = true)
            }
        }
    }
    if (gold == null) return
    if (gold.onlyOurs.isNotEmpty()) {
        centered("c-ours-head") {
            SectionHeader("Нашли только мы", "Новая находка или ложная тревога — проверьте глазами")
        }
        gold.onlyOurs.forEach { issue ->
            centered("c-ours-${issue.publicationId}-${issue.id}") {
                Box(Modifier.padding(bottom = 12.dp)) { IssueCard(store, issue, gold = false, withArticle = true) }
            }
        }
    }
    if (gold.missed.isNotEmpty()) {
        centered("c-missed-head") { SectionHeader("Пропустили", "Эти замечания эксперты нашли, а система нет") }
        gold.missed.forEach { issue ->
            centered("c-missed-${issue.publicationId}-${issue.id}") {
                Box(Modifier.padding(bottom = 12.dp)) { MissedIssueCard(profile, issue, withArticle = true) }
            }
        }
    }
}

@Composable
private fun TileRow(tiles: List<TileSpec>) {
    AdaptiveGrid(tiles.size, 200.dp, equalHeight = true) { index, modifier ->
        val tile = tiles[index]
        Block(modifier) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
                LabelWithInfo(tile.title, tile.info)
                Text(tile.value, Modifier.padding(vertical = 4.dp), style = T.tile)
                tile.note?.let { Text(it, style = T.small, color = C.fg2) }
                tile.badge?.let { (text, tone) -> Row(Modifier.padding(top = 6.dp)) { Badge(text, tone) } }
            }
        }
    }
}

@Composable
private fun TableHeader(headers: List<String>, sort: TableSort?, onSort: (Int) -> Unit) {
    val phone = LocalWidth.current == Width.PHONE
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (phone) {
            Text(headers.joinToString(" · "), style = T.small, color = C.fg2)
        } else {
            headers.forEachIndexed { index, header ->
                val active = sort?.column == index
                val arrow = if (active) (if (sort?.descending == true) " ↓" else " ↑") else ""
                Text(
                    header + arrow,
                    Modifier.weight(if (index == 0) 2f else 1f).press { onSort(index) },
                    style = T.small,
                    color = if (active) C.fg else C.fg2,
                    textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                    maxLines = 1,
                )
            }
        }
    }
}

private class TableLine(val title: String, val code: String?, val numbers: List<String>, val keys: List<Double>)

private fun List<TableLine>.sortedBy(sort: TableSort?): List<TableLine> {
    if (sort == null) return this
    val comparator: Comparator<TableLine> = if (sort.column == 0) compareBy { it.title.lowercase() } else compareBy { it.keys[sort.column - 1] }
    return sortedWith(if (sort.descending) comparator.reversed() else comparator)
}

@Composable
private fun TableRow(headers: List<String>, title: String, code: String?, numbers: List<String>, emphasizeLast: Boolean, monoCode: Boolean = false) {
    val phone = LocalWidth.current == Width.PHONE
    val modifier = Modifier.fillMaxWidth().topDivider(true, C.divider, 0.dp).padding(horizontal = 12.dp, vertical = 10.dp)
    if (phone) {
        Column(modifier) {
            Text(title, style = T.body)
            code?.let { Text(it, Modifier.subline(), style = if (monoCode) T.mono.copy(color = C.fg2) else T.small.copy(color = C.fg2)) }
            Text(headers.drop(1).zip(numbers).joinToString(" · ") { (header, value) -> "${header.lowercase()} $value" }, style = T.small, color = C.fg2)
        }
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(2f)) {
            Text(title, style = T.body)
            code?.let { Text(it, Modifier.subline(), style = if (monoCode) T.mono.copy(color = C.fg2) else T.small.copy(color = C.fg2)) }
        }
        numbers.forEachIndexed { index, value ->
            Text(
                value,
                Modifier.weight(1f),
                style = if (emphasizeLast && index == numbers.lastIndex) T.bodyMedium else T.body,
                textAlign = TextAlign.End,
            )
        }
    }
}
