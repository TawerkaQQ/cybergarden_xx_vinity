package cybergarden.vinity.domain.usecase.validation

import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.Violation

class IdentityRule : SubmissionRule {

    override fun check(submission: Submission): List<Violation> = submission.profile.allTables.flatMap { spec ->
        val rows = submission.rows(spec)
        val unsafe = rows.filterNot { SAFE_ID.matches(it.publicationId) }
            .map { Violation.error(spec.name, it, "недопустимый publication_id «${it.publicationId}»") }
        val empty = rows.filter { it.id.isEmpty() }
            .map { Violation.error(spec.name, it, "пустой ${spec.id}") }
        val repeated = rows.filter { it.id.isNotEmpty() }
            .groupingBy { it.publicationId to it.id }.eachCount()
            .filterValues { it > 1 }.keys
            .map { (publication, id) -> Violation.error(spec.name, publication, id, "${spec.id} повторяется") }
        unsafe + empty + repeated
    }

    private companion object {
        val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]*")
    }
}
