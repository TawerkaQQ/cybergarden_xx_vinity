package cybergarden.vinity.domain.usecase.validation

import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.Violation

class CoverageRule(private val articles: Set<String>) : SubmissionRule {

    override fun check(submission: Submission): List<Violation> {
        val table = submission.profile.publications.name
        val listed = submission.publications.map { it.publicationId }.toSet()
        return (articles - listed).sorted().map { Violation.error(table, it, null, "для $it.pdf нет строки") } +
            (listed - articles).sorted().map { Violation.error(table, it, null, "нет файла $it.pdf") }
    }
}
