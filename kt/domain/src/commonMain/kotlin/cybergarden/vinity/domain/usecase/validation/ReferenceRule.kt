package cybergarden.vinity.domain.usecase.validation

import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.Violation

class ReferenceRule : SubmissionRule {

    override fun check(submission: Submission): List<Violation> {
        val profile = submission.profile
        val known = profile.allTables.associate { spec ->
            spec.name to submission.rows(spec).map { it.publicationId to it.id }.toSet()
        }
        return profile.allTables.flatMap { spec ->
            spec.references.flatMap { (column, target) ->
                val targets = known.getValue(target)
                submission.rows(spec).flatMap { row ->
                    spec.cellValues(row, column).filterNot { (row.publicationId to it) in targets }.map {
                        Violation.error(spec.name, row, "$column = $it: нет такой записи в $target")
                    }
                }
            }
        }
    }
}
