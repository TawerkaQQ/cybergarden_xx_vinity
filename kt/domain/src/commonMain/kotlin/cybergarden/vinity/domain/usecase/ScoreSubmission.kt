package cybergarden.vinity.domain.usecase

import cybergarden.vinity.domain.model.MatchMode
import cybergarden.vinity.domain.model.Publications
import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.port.TextNormalizer

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

data class ArticleComparison(
    val extra: List<Record>,
    val missed: List<Record>,
    val matchedIssues: List<Record>,
    val missedIssues: List<Record>,
)

class ScoreSubmission {

    operator fun invoke(candidate: Submission, gold: Submission, mode: MatchMode): ScoreReport {
        val spec = gold.profile.values
        val scored = scoredPublications(candidate, gold)
        val candidateRows = scoredRows(candidate, scored)
        val goldRows = scoredRows(gold, scored)
        val matcher = ValueMatcher(spec, mode)

        fun countsBy(group: (Record) -> String?): Map<String, Counts> {
            val candidateGroups = candidateRows.groupBy(group)
            val goldGroups = goldRows.groupBy(group)
            return (candidateGroups.keys + goldGroups.keys).filterNotNull().associateWith { key ->
                matcher.match(candidateGroups[key].orEmpty(), goldGroups[key].orEmpty()).counts
            }
        }

        return ScoreReport(
            mode = mode,
            matchedBy = spec.match[mode].orEmpty(),
            publicationsScored = scored.size,
            publicationsInGold = gold.publications.size,
            allMentions = matcher.match(candidateRows, goldRows).counts,
            primaryRecords = matcher.match(
                candidateRows.filter { it[spec.duplicateOf] == null },
                goldRows.filter { it[spec.duplicateOf] == null },
            ).counts,
            byMetric = countsBy { it[spec.metric] }.entries
                .sortedWith(compareByDescending<Map.Entry<String, Counts>> { it.value.support }.thenBy { it.key })
                .associate { it.key to it.value },
            byPublication = countsBy { it.publicationId }.entries.sortedBy { it.key }.associate { it.key to it.value },
            scope = scopeAgreement(candidate, gold, scored),
        )
    }

    fun article(candidate: Submission, gold: Submission, publicationId: String, mode: MatchMode, normalizer: TextNormalizer): ArticleComparison {
        val only = setOf(publicationId)
        val values = ValueMatcher(gold.profile.values, mode).match(scoredRows(candidate, only), scoredRows(gold, only))
        val issues = IssueMatcher(normalizer).match(
            candidate.issues.filter { it.publicationId == publicationId },
            gold.issues.filter { it.publicationId == publicationId },
        )
        return ArticleComparison(
            extra = values.unmatchedCandidates,
            missed = values.unmatchedGold,
            matchedIssues = issues.pairs.map { it.first },
            missedIssues = issues.unmatchedGold,
        )
    }

    private fun scoredPublications(candidate: Submission, gold: Submission): Set<String> {
        val processed = candidate.publications.filterNot { it[Publications.IN_SCOPE].isNullOrBlank() }.map { it.publicationId } +
            candidate.values.map { it.publicationId }
        return processed.toSet() intersect gold.publications.map { it.publicationId }.toSet()
    }

    private fun scoredRows(submission: Submission, publications: Set<String>): List<Record> {
        val spec = submission.profile.values
        return submission.values.filter { it.publicationId in publications && spec.isScored(it) }
    }

    private fun scopeAgreement(candidate: Submission, gold: Submission, ids: Set<String>): ScopeAgreement {
        val expected = gold.publications.associate { it.publicationId to it[Publications.IN_SCOPE] }
        val judged = candidate.publications.filter { it.publicationId in ids && it[Publications.IN_SCOPE] != null }
        return ScopeAgreement(judged.count { it[Publications.IN_SCOPE] == expected[it.publicationId] }, judged.size)
    }
}
