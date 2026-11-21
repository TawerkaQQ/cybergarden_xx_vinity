package cybergarden.vinity.domain.usecase

import cybergarden.vinity.domain.model.Issues
import cybergarden.vinity.domain.model.MatchMode
import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.model.ValuesSpec
import cybergarden.vinity.domain.port.TextNormalizer
import kotlin.math.abs
import kotlin.math.max

data class Matching(
    val pairs: List<Pair<Record, Record>>,
    val unmatchedCandidates: List<Record>,
    val unmatchedGold: List<Record>,
) {
    val counts: Counts get() = Counts(pairs.size, unmatchedCandidates.size, unmatchedGold.size)
}

class ValueMatcher(spec: ValuesSpec, mode: MatchMode) {
    private val valueColumn = spec.value
    private val columns = spec.matchColumns(mode)
    private val compareValue = valueColumn in columns
    private val exactColumns = columns - valueColumn

    fun match(candidate: List<Record>, gold: List<Record>): Matching {
        val pool = gold.groupBy(::key).mapValues { it.value.toMutableList() }
        val pairs = mutableListOf<Pair<Record, Record>>()
        val unmatched = mutableListOf<Record>()
        candidate.forEach { row ->
            val bucket = pool[key(row)]
            val hit = bucket?.indexOfFirst { sameValue(it, row) } ?: -1
            if (bucket != null && hit >= 0) pairs += row to bucket.removeAt(hit) else unmatched += row
        }
        val taken = pairs.map { it.second.key() }.toSet()
        return Matching(pairs, unmatched, gold.filterNot { it.key() in taken })
    }

    private fun key(row: Record): List<String?> = listOf(row.publicationId) + exactColumns.map { row[it] }

    private fun sameValue(gold: Record, candidate: Record): Boolean {
        if (!compareValue) return true
        val expected = gold.number(valueColumn) ?: return false
        val actual = candidate.number(valueColumn) ?: return false
        return abs(expected - actual) <= VALUE_TOLERANCE * max(1.0, abs(expected))
    }

    private companion object {
        const val VALUE_TOLERANCE = 1e-6
    }
}

class IssueMatcher(private val normalizer: TextNormalizer) {

    fun match(candidate: List<Record>, gold: List<Record>): Matching {
        val pool = gold.toMutableList()
        val pairs = mutableListOf<Pair<Record, Record>>()
        val unmatched = mutableListOf<Record>()
        candidate.forEach { issue ->
            val hit = pool.indexOfFirst { same(issue, it) }
            if (hit >= 0) pairs += issue to pool.removeAt(hit) else unmatched += issue
        }
        return Matching(pairs, unmatched, pool)
    }

    private fun same(candidate: Record, gold: Record): Boolean {
        if (candidate.publicationId != gold.publicationId || candidate[Issues.CHECK] != gold[Issues.CHECK]) return false
        val page = candidate[Issues.PAGE]
        if (page != null && page == gold[Issues.PAGE]) return true
        val left = candidate[Issues.FRAGMENT]?.let(normalizer::normalize).orEmpty()
        val right = gold[Issues.FRAGMENT]?.let(normalizer::normalize).orEmpty()
        val unplaced = page == null && gold[Issues.PAGE] == null && left.isEmpty() && right.isEmpty()
        return unplaced || (left.isNotEmpty() && right.isNotEmpty() && (left in right || right in left))
    }
}
