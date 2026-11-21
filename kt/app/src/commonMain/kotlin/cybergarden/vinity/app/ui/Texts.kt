package cybergarden.vinity.app.ui

import cybergarden.vinity.api.ArticleSummaryDto
import cybergarden.vinity.api.IssueReviewStateDto
import cybergarden.vinity.api.ProcessingDto
import cybergarden.vinity.api.ProcessingStateDto
import cybergarden.vinity.api.ProfileDto
import cybergarden.vinity.api.QuoteStatusDto
import cybergarden.vinity.api.SetRef
import cybergarden.vinity.api.SourceDto
import cybergarden.vinity.api.ValueDto
import cybergarden.vinity.api.ValueKindDto
import cybergarden.vinity.api.ValueRefDto
import cybergarden.vinity.api.ValueReviewStateDto
import kotlin.math.abs
import kotlin.math.roundToInt

class Word(val one: String, val few: String, val many: String)

val Values = Word("значение", "значения", "значений")
val Issues = Word("замечание", "замечания", "замечаний")
val Articles = Word("статья", "статьи", "статей")
val Pages = Word("страница", "страницы", "страниц")

fun plural(count: Int, word: Word): String {
    val form = when {
        count % 100 in 11..14 -> word.many
        count % 10 == 1 -> word.one
        count % 10 in 2..4 -> word.few
        else -> word.many
    }
    return "$count $form"
}

private val superscripts = mapOf('2' to "²", '3' to "³")

fun unitText(unit: String?): String {
    if (unit.isNullOrBlank()) return ""
    if (unit == "fraction") return "доля"
    return unit.replace("Ohm", "Ω").replace("*", "·").replace(Regex("(?<=[a-zA-Z])([23])$")) { superscripts[it.value[0]].orEmpty() }
}

fun withUnit(number: String, unit: String?): String {
    val text = unitText(unit)
    return when {
        text.isEmpty() || endsWithUnit(number, unit) || endsWithUnit(number, text) -> number
        text == "доля" -> "$number (доля)"
        else -> "$number $text"
    }
}

private fun endsWithUnit(number: String, unit: String?): Boolean {
    val trimmed = number.trimEnd()
    if (unit.isNullOrBlank() || !trimmed.endsWith(unit)) return false
    val before = trimmed.dropLast(unit.length).lastOrNull() ?: return false
    return before.isDigit() || before.isWhitespace()
}

fun ValueDto.amount(): String = withUnit(value ?: rawValue ?: "—", if (value != null) unit else rawUnit)

fun ValueDto.fixedAmount(): String = review?.takeIf { it.state == ValueReviewStateDto.FIXED }?.value?.let { withUnit(it, unit) } ?: amount()

fun ValueDto.written(): String? {
    val raw = rawValue?.takeIf { it.isNotBlank() } ?: return null
    val text = withUnit(raw, rawUnit)
    return text.takeIf { it != amount() }
}

fun ValueRefDto.chip(): String = listOfNotNull(metric, value?.let { withUnit(it, unit) }, page?.let { "стр. $it" }).joinToString(" · ")

fun ValueDto.place(): String = when (kind) {
    ValueKindDto.TABLE -> table?.lowercaseFirst() ?: "таблица"
    ValueKindDto.FIGURE -> figure?.lowercaseFirst() ?: "рисунок"
    ValueKindDto.TEXT -> section?.let { "текст, $it" } ?: "текст"
}

fun ValueDto.pageAndPlace(): String = listOfNotNull(page?.let { "стр. $it" }, place()).joinToString(", ")

private fun String.lowercaseFirst(): String {
    val trimmed = trim()
    val words = trimmed.split(' ')
    val first = words.first()
    val lowered = if (first.length > 1 && first.all { it.isUpperCase() || !it.isLetter() }) first.lowercase() else first.replaceFirstChar { it.lowercase() }
    return (listOf(lowered) + words.drop(1)).joinToString(" ")
        .replace(Regex("^table", RegexOption.IGNORE_CASE), "таблица")
        .replace(Regex("^(figure|fig\\.?)", RegexOption.IGNORE_CASE), "рисунок")
}

fun ValueKindDto.title(): String = when (this) {
    ValueKindDto.TABLE -> "Таблица"
    ValueKindDto.TEXT -> "Текст"
    ValueKindDto.FIGURE -> "Рисунок"
}

fun ValueKindDto.how(): String = when (this) {
    ValueKindDto.TABLE -> "Ячейка таблицы"
    ValueKindDto.TEXT -> "Предложение в тексте"
    ValueKindDto.FIGURE -> "Подпись на рисунке"
}

fun variantTitle(variant: String?): String = when (variant?.lowercase()) {
    null -> "—"
    "pypdf" -> "pypdf"
    "pymupdf" -> "PyMuPDF"
    "ocr" -> "OCR (Tesseract)"
    "pdfbox" -> "PDFBox"
    "row_label" -> "ссылка на строку таблицы"
    else -> variant
}

fun QuoteStatusDto.found(): Boolean = this == QuoteStatusDto.FOUND || this == QuoteStatusDto.ROW_LABEL

fun ProfileDto?.metricTitle(code: String?): String {
    if (code == null) return "—"
    return this?.metrics?.firstOrNull { it.code == code }?.title ?: code
}

fun ProfileDto?.metricExplain(code: String?): String? = this?.metrics?.firstOrNull { it.code == code }?.explain

fun ProfileDto?.checkTitle(name: String?): String {
    if (name == null) return "Замечание"
    return this?.checks?.firstOrNull { it.name == name }?.title ?: name.replace('_', ' ').trim().replaceFirstChar { it.uppercase() }
}

fun ProfileDto?.checkRule(name: String?): String? = this?.checks?.firstOrNull { it.name == name }?.rule

fun ProfileDto?.aggregationTitle(key: String?): String? = key?.let { this?.aggregations?.get(it) ?: it }

fun SourceDto.title(): String = when (this) {
    SourceDto.RESULTS -> "результат системы"
    SourceDto.GOLD -> "эталон экспертов"
}

const val BeyondGoldHint = "У экспертов этого значения нет, но цитата дословно найдена на странице: возможно, эксперты его пропустили"

const val RepeatHint = "То же значение метрики с той же единицей есть в другой статье набора: похоже, один результат описан разными авторами"

fun durationText(seconds: Int): String = if (seconds < 60) "$seconds с" else "${seconds / 60} мин"

fun ProcessingDto?.shown(): Boolean = this != null && (active || state == ProcessingStateDto.FAILED)

fun ProcessingDto.title(): String = when (state) {
    ProcessingStateDto.QUEUED -> "В очереди"
    ProcessingStateDto.RUNNING -> "Обрабатывается" + (seconds?.let { " · ${durationText(it)}" } ?: "")
    ProcessingStateDto.DONE -> "Обработана"
    ProcessingStateDto.FAILED -> "Ошибка обработки"
}

fun ProcessingDto.tone(): Tone = when (state) {
    ProcessingStateDto.QUEUED -> Tone.INFO
    ProcessingStateDto.RUNNING -> Tone.PRI
    ProcessingStateDto.DONE -> Tone.OK
    ProcessingStateDto.FAILED -> Tone.ERR
}

fun ProcessingDto.hint(): String? = if (state == ProcessingStateDto.FAILED) problem else step

fun setTitle(ref: SetRef, uploads: Boolean): String = if (uploads) "загруженные" else ref.set

fun ValueReviewStateDto.title(): String = when (this) {
    ValueReviewStateDto.CONFIRMED -> "Подтверждено"
    ValueReviewStateDto.FIXED -> "Исправлено"
    ValueReviewStateDto.REJECTED -> "Отклонено"
}

fun ValueReviewStateDto.tone(): Tone = when (this) {
    ValueReviewStateDto.CONFIRMED -> Tone.OK
    ValueReviewStateDto.FIXED -> Tone.PRI
    ValueReviewStateDto.REJECTED -> Tone.NEUTRAL
}

fun IssueReviewStateDto.title(): String = when (this) {
    IssueReviewStateDto.CONFIRMED -> "Подтверждено референтом"
    IssueReviewStateDto.DISMISSED -> "Снято референтом"
}

fun ArticleSummaryDto.meta(): String = listOfNotNull(
    id,
    year,
    pageCount?.let { "$it стр." },
    if (inScope == false || !processed) null else plural(valueCount, Values),
    reviewed.takeIf { it > 0 }?.let { "проверено $it из $valueCount" },
).joinToString(" · ")

fun authorsList(authors: String?): List<String> = authors.orEmpty().split(';', ',').map { it.trim() }.filter { it.isNotEmpty() }

fun number(value: Double): String {
    if (value == value.roundToInt().toDouble() && abs(value) < 1e9) return value.roundToInt().toString()
    val scaled = (value * 1000).roundToInt() / 1000.0
    return scaled.toString().trimEnd('0').trimEnd('.')
}

fun fixed2(value: Double): String {
    val scaled = (value * 100).roundToInt()
    val sign = if (scaled < 0) "-" else ""
    val absolute = abs(scaled)
    return "$sign${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
}

fun percent(part: Int, whole: Int): String {
    if (whole == 0) return "—"
    val tenths = (part * 1000.0 / whole).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10} %" else "${tenths / 10}.${tenths % 10} %"
}

fun sizeText(bytes: Long): String {
    val tenths = (bytes * 10.0 / 1_048_576).roundToInt()
    return "${tenths / 10},${tenths % 10} МБ"
}
