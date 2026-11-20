package cybergarden.vinity.domain.usecase

import cybergarden.vinity.domain.model.MatchMode
import cybergarden.vinity.domain.model.Publications
import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.ValuesSpec
import kotlin.math.abs
import kotlin.math.max

data class Counts(val truePositive: Int = 0, val falsePositive: Int = 0, val falseNegative: Int = 0) {
    val precision: Double get() = ratio(truePositive, truePositive + falsePositive)
    val recall: Double get() = ratio(truePositive, truePositive + falseNegative)
    val f1: Double get() = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
    val support: Int get() = truePositive + falseNegative

    private fun ratio(part: Int, whole: Int) = if (whole == 0) 0.0 else part.toDouble() / whole
}

data class ScopeAgreement(val agreed: Int, val total: Int)

data class ScoreReport(
    val mode: MatchMode,
    val matchedBy: List<String>,
    val publicationsScored: Int,
    val publicationsInGold: Int,
    val allMentions: Counts,
    val primaryRecords: Counts,
    val byMetric: Map<String, Counts>,
    val byPublication: Map<String, Counts>,
    val scope: ScopeAgreement,
)

class ScoreSubmission {

    operator fun invoke(candidate: Submission, gold: Submission, mode: MatchMode): ScoreReport {
        val spec = gold.profile.values
        val scored = candidate.publications.map { it.publicationId }.toSet() intersect
            gold.publications.map { it.publicationId }.toSet()
        val candidateRows = candidate.values.filter { it.publicationId in scored && spec.isScored(it) }
        val goldRows = gold.values.filter { it.publicationId in scored && spec.isScored(it) }
        val matcher = Matcher(spec, spec.matchColumns(mode))

        fun countsBy(group: (Record) -> String?): Map<String, Counts> {
            val candidateGroups = candidateRows.groupBy(group)
            val goldGroups = goldRows.groupBy(group)
            return (candidateGroups.keys + goldGroups.keys).filterNotNull().associateWith { key ->
                matcher.count(candidateGroups[key].orEmpty(), goldGroups[key].orEmpty())
            }
        }

        return ScoreReport(
            mode = mode,
            matchedBy = spec.match[mode].orEmpty(),
            publicationsScored = scored.size,
            publicationsInGold = gold.publications.size,
            allMentions = matcher.count(candidateRows, goldRows),
            primaryRecords = matcher.count(
                candidateRows.filter { it[spec.duplicateOf] == null },
                goldRows.filter { it[spec.duplicateOf] == null },
            ),
            byMetric = countsBy { it[spec.metric] }.entries
                .sortedWith(compareByDescending<Map.Entry<String, Counts>> { it.value.support }.thenBy { it.key })
                .associate { it.key to it.value },
            byPublication = countsBy { it.publicationId }.entries.sortedBy { it.key }.associate { it.key to it.value },
            scope = scopeAgreement(candidate, gold, scored),
        )
    }

    private class Matcher(spec: ValuesSpec, columns: List<String>) {
        private val valueColumn = spec.value
        private val compareValue = valueColumn in columns
        private val exactColumns = columns - valueColumn

        fun count(candidate: List<Record>, gold: List<Record>): Counts {
            val pool = gold.groupBy(::key).mapValues { it.value.toMutableList() }
            val truePositive = candidate.count { row ->
                val bucket = pool[key(row)] ?: return@count false
                val hit = bucket.indexOfFirst { sameValue(it, row) }
                if (hit >= 0) bucket.removeAt(hit)
                hit >= 0
            }
            return Counts(truePositive, candidate.size - truePositive, gold.size - truePositive)
        }

        private fun key(row: Record): List<String?> = listOf(row.publicationId) + exactColumns.map { row[it] }

        private fun sameValue(gold: Record, candidate: Record): Boolean {
            if (!compareValue) return true
            val expected = gold.number(valueColumn) ?: return false
            val actual = candidate.number(valueColumn) ?: return false
            return abs(expected - actual) <= VALUE_TOLERANCE * max(1.0, abs(expected))
        }
    }

    private fun scopeAgreement(candidate: Submission, gold: Submission, ids: Set<String>): ScopeAgreement {
        val expected = gold.publications.associate { it.publicationId to it[Publications.IN_SCOPE] }
        val agreed = candidate.publications.count { it.publicationId in ids && it[Publications.IN_SCOPE] == expected[it.publicationId] }
        return ScopeAgreement(agreed, ids.size)
    }

    private companion object {
        const val VALUE_TOLERANCE = 1e-6
    }
}
