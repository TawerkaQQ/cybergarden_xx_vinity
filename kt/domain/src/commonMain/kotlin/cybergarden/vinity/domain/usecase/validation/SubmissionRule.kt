package cybergarden.vinity.domain.usecase.validation

import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.TableSpec
import cybergarden.vinity.domain.model.Violation

fun interface SubmissionRule {
    fun check(submission: Submission): List<Violation>
}

internal fun TableSpec.cellValues(row: Record, column: String): List<String> =
    if (column in lists) row.list(column) else listOfNotNull(row[column])
