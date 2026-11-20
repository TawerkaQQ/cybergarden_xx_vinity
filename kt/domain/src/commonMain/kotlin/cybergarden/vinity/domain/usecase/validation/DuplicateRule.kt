package cybergarden.vinity.domain.usecase.validation

import cybergarden.vinity.domain.model.DecimalText
import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.Violation

class DuplicateRule : SubmissionRule {

    override fun check(submission: Submission): List<Violation> {
        val spec = submission.profile.values
        val index = submission.values.associateBy { it.publicationId to it.id }
        val keyColumns = spec.duplicateKey.map(spec::column)

        fun target(row: Record): Record? = row[spec.duplicateOf]?.let { index[row.publicationId to it] }

        return submission.values.filter { it[spec.duplicateOf] != null }.mapNotNull { row ->
            val primary = target(row) ?: return@mapNotNull null
            val chain = generateSequence(row, ::target).take(index.size + 1).map { it.id }.toList()
            when {
                primary.id == row.id -> Violation.error(spec.table, row, "${spec.duplicateOf} ссылается на саму запись")
                chain.size != chain.toSet().size -> Violation.error(spec.table, row, "цикл ${spec.duplicateOf}")
                else -> keyColumns.filterNot { same(row[it], primary[it], it == spec.value) }
                    .takeIf { it.isNotEmpty() }
                    ?.let { Violation.error(spec.table, row, "отличается от ${primary.id}: ${it.joinToString()}") }
            }
        }
    }

    private fun same(left: String?, right: String?, numeric: Boolean): Boolean =
        if (numeric && left != null && right != null) DecimalText.sameAtReportedPrecision(left, right) else left == right
}
