package cybergarden.vinity.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
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
import kotlin.math.roundToInt

private val Ink = Color(0xFF1F1F1F)
private val Times = Word("раз", "раза", "раз")
private const val CHART_METRICS = 15
private const val TABLE_METRICS = 30
private const val UNIT_METRICS = 40

private class TileSpec(val title: String, val value: String, val note: String, val badge: Pair<String, Tone>? = null)

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
    val subtitle = listOfNotNull(profile?.title, set?.let { "набор ${setTitle(it.ref, it.uploads)}" }, "вкладки повторяют критерии кейса").joinToString(" · ")
    ScreenList("quality-${set?.ref?.path}-${set?.source}-$tab") {
        centered("head") { PageHeader("Качество", subtitle) }
        centered("tabs") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
                Segmented(QualityTab.entries.map { it to it.title() }, tab, store::selectQualityTab)
            }
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
            EmptyState("Точность и полноту считаем, когда у набора есть и результат системы, и эталон экспертов. Здесь чего-то из двух нет, поэтому сравнивать не с чем. Остальные вкладки работают.")
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Segmented(listOf(true to "Строгое сравнение", false to "Мягкое"), strict, store::selectStrict)
            Segmented(listOf(false to "Все упоминания", true to "Только основные"), primaryOnly, { store.primaryOnly = it })
        }
    }
    centered("modes-hint") { Muted(modeHint, Modifier.padding(top = 8.dp, bottom = 12.dp), small = true) }
    centered("tiles") {
        TileRow(
            listOf(
                TileSpec("Точность", fixed2(counts.precision), "Из 100 найденных значений ${hundred(counts.precision)} есть у экспертов. Остальные лишние."),
                TileSpec("Полнота", fixed2(counts.recall), "Из 100 значений экспертов система нашла ${hundred(counts.recall)}."),
                TileSpec("F1", fixed2(counts.f1), "Общая оценка: среднее гармоническое точности и полноты. 1 — идеально."),
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
                Muted("Область статьи определена верно в ${score.scopeAgreed} из ${score.scopeTotal} статей.", Modifier.padding(top = 8.dp), small = true)
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
    centered("table-columns") { BlockPiece(first = true, last = false) { TableHeader(headers) } }
    shown.forEachIndexed { index, metric ->
        centered("table-row-$index") {
            BlockPiece(first = false, last = index == shown.lastIndex) {
                TableRow(
                    headers = headers,
                    title = metric.title,
                    code = metric.code.takeIf { it != metric.title },
                    numbers = with(metric.counts) {
                        listOf(truePositive.toString(), falsePositive.toString(), falseNegative.toString(), fixed2(precision), fixed2(recall), fixed2(f1))
                    },
                    emphasizeLast = true,
                )
            }
        }
    }
}

private fun metricTip(metric: MetricScore): String = with(metric.counts) {
    "${metric.title}\nТочность ${fixed2(precision)} · Полнота ${fixed2(recall)} · F1 ${fixed2(f1)}\nСовпало $truePositive · лишних $falsePositive · пропущено $falseNegative"
}

private fun hundred(share: Double): Int = (share * 100).roundToInt()

private fun LazyListScope.quotesTab(store: AppStore, quotes: QuoteQualityDto) {
    centered("q-intro") {
        Intro("Проверяем так же, как организаторы: ищем цитату дословно в тексте страницы — из pypdf, PyMuPDF или распознавания скана.")
    }
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
                    "Значения из таблиц и текста",
                    percent(quotes.textFound, quotes.textValues),
                    "цитата найдена у ${quotes.textFound} из ${quotes.textValues}. Цель — не меньше 95 %.",
                    if (quotes.textValues == 0) null else if (goal) "цель достигнута" to Tone.OK else "ниже цели" to Tone.ERR,
                ),
                TileSpec("Все значения", percent(quotes.found, quotes.values), "${quotes.found} из ${quotes.values}, включая значения с рисунков"),
                TileSpec("Без цитаты", without.toString(), withoutNote),
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
    centered("n-intro") { Intro("Всё, что система поменяла в записи числа, видно рядом со значением: «в статье → у нас».") }
    if (normalization.values == 0) {
        centered("n-empty") { EmptyState("Значений пока нет.") }
        return
    }
    val complete = normalization.metrics.count { it.values > 0 && it.inUnit == it.values }
    centered("n-tiles") {
        TileRow(
            listOf(
                TileSpec("В целевых единицах", "$complete из ${normalization.metrics.size}", "показателей набора полностью приведены к единицам профиля"),
                TileSpec("Преобразовано значений", normalization.converted.toString(), "остальные ${normalization.values - normalization.converted} записаны в статье уже в нужном виде"),
                TileSpec(
                    "Словарные поля",
                    percent(normalization.vocabularyValid, normalization.vocabularyChecked),
                    if (normalization.vocabularyChecked == 0) {
                        "в данных нет заполненных словарных колонок"
                    } else {
                        "${normalization.vocabularyValid} из ${normalization.vocabularyChecked} значений словарных колонок — из допустимых"
                    },
                ),
            ),
        )
    }
    if (normalization.conversions.isNotEmpty()) {
        centered("n-conversions-head") { SectionHeader("Преобразования", "Как запись из статьи переведена в единицы профиля. Нажмите, чтобы открыть пример") }
        blockItems(normalization.conversions.withIndex().toList(), key = { "n-conversion-${it.index}" }) { (_, conversion), first ->
            Row(
                Modifier.fillMaxWidth().topDivider(!first, C.divider).press { store.openValue(conversion.publicationId, conversion.example.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${unitText(conversion.from).ifEmpty { "без единицы" }} → ${unitText(conversion.to).ifEmpty { "без единицы" }}", style = T.body)
                    Muted("например ${conversion.publicationId}: «${conversion.exampleRaw}» → ${withUnit(conversion.example.value ?: "—", conversion.example.unit)}", small = true)
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
                    if (title != metric.metric) Muted(metric.metric, small = true)
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
    centered("c-intro") { Intro("Правила проверки задаёт профиль области. Каждое замечание — с числами с обеих сторон и цитатами.") }
    val gold = checks.gold
    val reviewed = checks.confirmed + checks.dismissed
    centered("c-tiles") {
        TileRow(
            buildList {
                if (gold != null) {
                    add(TileSpec("Совпало с экспертами", "${gold.matched} из ${gold.goldTotal}", "замечаний эталона система нашла сама"))
                    add(TileSpec("Нашли только мы", gold.onlyOurs.size.toString(), "новая находка или ложная тревога — решает референт"))
                } else {
                    val note = if (source == SourceDto.GOLD) "замечания экспертов из эталона набора" else "эталона нет, сравнивать не с чем"
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
    centered("c-table-columns") { BlockPiece(first = true, last = false) { TableHeader(headers) } }
    checks.byCheck.forEachIndexed { index, check ->
        centered("c-check-$index") {
            BlockPiece(first = false, last = index == checks.byCheck.lastIndex) {
                TableRow(
                    headers = headers,
                    title = profile.checkTitle(check.check),
                    code = check.check,
                    numbers = if (gold != null) {
                        listOf(check.found.toString(), (check.gold ?: 0).toString(), (check.matched ?: 0).toString())
                    } else {
                        listOf(check.found.toString())
                    },
                    emphasizeLast = false,
                    monoCode = true,
                )
            }
        }
    }
    if (gold == null) return
    if (gold.onlyOurs.isNotEmpty()) {
        centered("c-ours-head") {
            SectionHeader("Нашли только мы", "Проверьте глазами: если это ошибка в статье, это сильный пример для презентации")
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
private fun Intro(text: String) {
    Muted(text, Modifier.padding(bottom = 12.dp), small = true)
}

@Composable
private fun TileRow(tiles: List<TileSpec>) {
    AdaptiveGrid(tiles.size, 200.dp, equalHeight = true) { index, modifier ->
        val tile = tiles[index]
        Block(modifier) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
                Text(tile.title, style = T.body, color = C.fg2)
                Text(tile.value, Modifier.padding(vertical = 4.dp), style = T.tile)
                Text(tile.note, style = T.small, color = C.fg2)
                tile.badge?.let { (text, tone) -> Row(Modifier.padding(top = 6.dp)) { Badge(text, tone) } }
            }
        }
    }
}

@Composable
private fun TableHeader(headers: List<String>) {
    val phone = LocalWidth.current == Width.PHONE
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (phone) {
            Text(headers.joinToString(" · "), style = T.small, color = C.fg2)
        } else {
            Text(headers.first(), Modifier.weight(2f), style = T.small, color = C.fg2)
            headers.drop(1).forEach { Text(it, Modifier.weight(1f), style = T.small, color = C.fg2, textAlign = TextAlign.End) }
        }
    }
}

@Composable
private fun TableRow(headers: List<String>, title: String, code: String?, numbers: List<String>, emphasizeLast: Boolean, monoCode: Boolean = false) {
    val phone = LocalWidth.current == Width.PHONE
    val modifier = Modifier.fillMaxWidth().topDivider(true, C.divider, 0.dp).padding(horizontal = 12.dp, vertical = 10.dp)
    if (phone) {
        Column(modifier) {
            Text(title, style = T.body)
            code?.let { Text(it, style = if (monoCode) T.mono.copy(color = C.fg2) else T.small.copy(color = C.fg2)) }
            Text(headers.drop(1).zip(numbers).joinToString(" · ") { (header, value) -> "${header.lowercase()} $value" }, style = T.small, color = C.fg2)
        }
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(2f)) {
            Text(title, style = T.body)
            code?.let { Text(it, style = if (monoCode) T.mono.copy(color = C.fg2) else T.small.copy(color = C.fg2)) }
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
