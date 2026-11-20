package cybergarden.vinity.domain.usecase

import cybergarden.vinity.domain.model.ExtractionStatus
import cybergarden.vinity.domain.model.MetricResult
import cybergarden.vinity.domain.model.Submission
import kotlin.math.abs
import kotlin.math.max

enum class MatchMode { LOOSE, STRICT }

data class Counts(val truePositive: Int = 0, val falsePositive: Int = 0, val falseNegative: Int = 0) {
    val precision: Double get() = ratio(truePositive, truePositive + falsePositive)
    val recall: Double get() = ratio(truePositive, truePositive + falseNegative)
    val f1: Double get() = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)

    operator fun plus(other: Counts) = Counts(
        truePositive + other.truePositive,
        falsePositive + other.falsePositive,
        falseNegative + other.falseNegative,
    )

    private fun ratio(part: Int, whole: Int) = if (whole == 0) 0.0 else part.toDouble() / whole
}

data class ScopeAgreement(val agreed: Int, val total: Int)

data class ScoreReport(
    val mode: MatchMode,
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
        val scoredIds = candidate.publications.map { it.publicationId }.toSet() intersect
            gold.publications.map { it.publicationId }.toSet()
        val candidateRows = candidate.results.filter { it.publicationId in scoredIds && it.isScored() }
        val goldRows = gold.results.filter { it.publicationId in scoredIds && it.isScored() }

        val byPublication = scoredIds.sorted().associateWith { id ->
            match(candidateRows.filter { it.publicationId == id }, goldRows.filter { it.publicationId == id }, mode)
        }
        val byMetric = (candidateRows + goldRows).map { it.metricName }.distinct().sorted().associateWith { metric ->
            scoredIds.fold(Counts()) { acc, id ->
                acc + match(
                    candidateRows.filter { it.publicationId == id && it.metricName == metric },
                    goldRows.filter { it.publicationId == id && it.metricName == metric },
                    mode,
                )
            }
        }
        val primaryRecords = scoredIds.fold(Counts()) { acc, id ->
            acc + match(
                candidateRows.filter { it.publicationId == id && it.duplicateOf == null },
                goldRows.filter { it.publicationId == id && it.duplicateOf == null },
                mode,
            )
        }

        return ScoreReport(
            mode = mode,
            publicationsScored = scoredIds.size,
            publicationsInGold = gold.publications.size,
            allMentions = byPublication.values.fold(Counts()) { acc, counts -> acc + counts },
            primaryRecords = primaryRecords,
            byMetric = byMetric,
            byPublication = byPublication,
            scope = scopeAgreement(candidate, gold, scoredIds),
        )
    }

    private fun MetricResult.isScored() =
        scoredMetric != null && extractionStatus == ExtractionStatus.EXTRACTED && normalizedValue != null

    private fun match(candidate: List<MetricResult>, gold: List<MetricResult>, mode: MatchMode): Counts {
        val unmatched = gold.toMutableList()
        var truePositive = 0
        candidate.forEach { row ->
            val hit = unmatched.firstOrNull { it.matches(row, mode) }
            if (hit != null) {
                unmatched.remove(hit)
                truePositive++
            }
        }
        return Counts(truePositive, candidate.size - truePositive, unmatched.size)
    }

    private fun MetricResult.matches(other: MetricResult, mode: MatchMode): Boolean {
        val base = metricName == other.metricName &&
            origin == other.origin &&
            sameValue(normalizedValue, other.normalizedValue)
        return when (mode) {
            MatchMode.LOOSE -> base
            MatchMode.STRICT -> base && aggregation == other.aggregation && source.page == other.source.page
        }
    }

    private fun sameValue(left: Double?, right: Double?): Boolean {
        if (left == null || right == null) return false
        return abs(left - right) <= VALUE_TOLERANCE * max(1.0, abs(right))
    }

    private fun scopeAgreement(candidate: Submission, gold: Submission, ids: Set<String>): ScopeAgreement {
        val goldScope = gold.publications.associate { it.publicationId to it.inScope }
        val agreed = candidate.publications.count { it.publicationId in ids && it.inScope == goldScope[it.publicationId] }
        return ScopeAgreement(agreed, ids.size)
    }

    private companion object {
        const val VALUE_TOLERANCE = 1e-6
    }
}
