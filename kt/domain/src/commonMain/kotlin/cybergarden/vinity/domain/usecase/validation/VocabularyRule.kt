package cybergarden.vinity.domain.usecase.validation

import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.Violation

class VocabularyRule : SubmissionRule {

    override fun check(submission: Submission): List<Violation> = submission.profile.allTables.flatMap { spec ->
        spec.vocabularies.flatMap { (column, allowed) ->
            submission.rows(spec).flatMap { row ->
                spec.cellValues(row, column).filterNot { it in allowed }.map {
                    Violation.error(spec.name, row, "$column = «$it», допустимо: ${allowed.joinToString()}")
                }
            }
        }
    }
}
