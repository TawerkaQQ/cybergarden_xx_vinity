package cybergarden.vinity.domain.usecase

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

enum class ValueSort { ARTICLE, METRIC, VALUE, RAW, RECORD, AGGREGATION, PLACE, STATUS }

enum class ReviewFilter { UNCHECKED, CHECKED, NO_QUOTE }

data class ValueQuery(
    val text: String = "",
    val metrics: Set<String> = emptySet(),
    val kinds: Set<ValueKind> = emptySet(),
    val review: Set<ReviewFilter> = emptySet(),
    val withIssues: Boolean = false,
    val hideDuplicates: Boolean = false,
    val min: Double? = null,
    val max: Double? = null,
    val sort: ValueSort = ValueSort.ARTICLE,
    val descending: Boolean = false,
)

data class Bin(val from: Double, val to: Double, val count: Int)

data class ValueTable(val matching: List<ValueFacts>, val histogram: List<Bin>?)

class QueryValues {

    operator fun invoke(catalog: ValueCatalog, query: ValueQuery): ValueTable {
        val spec = catalog.index.profile.values
        val single = query.metrics.singleOrNull()
        val text = query.text.trim().lowercase()
        val base = catalog.facts.filter { fact ->
            (query.metrics.isEmpty() || fact.row[spec.metric].orEmpty() in query.metrics) &&
                (query.kinds.isEmpty() || fact.kind in query.kinds) &&
                (query.review.isEmpty() || query.review.any { it.accepts(fact) }) &&
                (!query.withIssues || fact.activeIssues > 0) &&
                (!query.hideDuplicates || fact.row[spec.duplicateOf] == null) &&
                (text.isEmpty() || catalog.searchText[fact.row.key()].orEmpty().contains(text))
        }
        val ranged = if (single == null || (query.min == null && query.max == null)) base else base.filter { fact ->
            val number = fact.row.number(spec.value) ?: return@filter false
            (query.min == null || number >= query.min) && (query.max == null || number <= query.max)
        }
        val histogram = single?.let { histogram(base.mapNotNull { it.row.number(spec.value) }) }
        return ValueTable(ranged.sortedWith(comparator(catalog, query)), histogram)
    }

    private fun ReviewFilter.accepts(fact: ValueFacts): Boolean = when (this) {
        ReviewFilter.UNCHECKED -> fact.review == null
        ReviewFilter.CHECKED -> fact.review != null
        ReviewFilter.NO_QUOTE -> fact.quote.status in setOf(QuoteStatus.MISSING, QuoteStatus.ELSEWHERE, QuoteStatus.NO_PAGE_TEXT)
    }

    private fun comparator(catalog: ValueCatalog, query: ValueQuery): Comparator<ValueFacts> {
        val spec = catalog.index.profile.values
        val natural = compareBy<ValueFacts, String>(NaturalOrder) { it.row.publicationId }
            .thenBy(NaturalOrder) { it.row.id }
        val primary: Comparator<ValueFacts> = when (query.sort) {
            ValueSort.ARTICLE -> compareBy(NaturalOrder) { it.row.publicationId }
            ValueSort.METRIC -> compareBy { it.row[spec.metric].orEmpty().lowercase() }
            ValueSort.VALUE -> compareBy(nullsLast()) { it.row.number(spec.value) }
            ValueSort.RAW -> compareBy(NaturalOrder) { it.row[spec.rawValue].orEmpty().lowercase() }
            ValueSort.RECORD -> compareBy { it.record?.label.orEmpty().lowercase() }
            ValueSort.AGGREGATION -> compareBy(nullsLast()) { it.row[spec.aggregation]?.lowercase() }
            ValueSort.PLACE -> compareBy<ValueFacts, String>(NaturalOrder) { it.row.publicationId }
                .thenBy { it.row.integer(spec.page) ?: Int.MAX_VALUE }
            ValueSort.STATUS -> compareByDescending { it.activeIssues }
        }
        return (if (query.descending) primary.reversed() else primary).then(natural)
    }

    private fun histogram(numbers: List<Double>): List<Bin>? {
        if (numbers.size < 2) return null
        val low = numbers.min()
        val high = numbers.max()
        val raw = ((high - low) / BIN_TARGET).takeIf { it > 0 } ?: 1.0
        val magnitude = 10.0.pow(floor(log10(raw)))
        val step = STEPS.map { it * magnitude }.first { it >= raw }
        val start = floor(low / step) * step
        val count = floor((high - start) / step + EPSILON).toInt() + 1
        val counts = IntArray(count)
        numbers.forEach { counts[min(count - 1, floor((it - start) / step + EPSILON).toInt())]++ }
        return counts.indices.map { Bin(round(start + it * step), round(start + (it + 1) * step), counts[it]) }
    }

    private fun round(value: Double): Double = kotlin.math.round(value * ROUNDING) / ROUNDING

    private companion object {
        const val BIN_TARGET = 6
        const val EPSILON = 1e-9
        const val ROUNDING = 1e6
        val STEPS = listOf(1.0, 2.0, 2.5, 5.0, 10.0)
    }
}

object NaturalOrder : Comparator<String> {

    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            if (a[i].isDigit() && b[j].isDigit()) {
                val endA = digitsEnd(a, i)
                val endB = digitsEnd(b, j)
                val startA = significant(a, i, endA)
                val startB = significant(b, j, endB)
                if (endA - startA != endB - startB) return (endA - startA) - (endB - startB)
                for (k in 0 until endA - startA) {
                    val difference = a[startA + k] - b[startB + k]
                    if (difference != 0) return difference
                }
                i = endA
                j = endB
            } else {
                if (a[i] != b[j]) return a[i] - b[j]
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }

    private fun digitsEnd(text: String, from: Int): Int {
        var end = from
        while (end < text.length && text[end].isDigit()) end++
        return end
    }

    private fun significant(text: String, from: Int, end: Int): Int {
        var start = from
        while (start < end - 1 && text[start] == '0') start++
        return start
    }
}
