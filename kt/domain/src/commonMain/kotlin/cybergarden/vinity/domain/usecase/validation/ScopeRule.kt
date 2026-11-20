package cybergarden.vinity.domain.usecase.validation

import cybergarden.vinity.domain.model.Publications
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.Violation

class ScopeRule : SubmissionRule {

    override fun check(submission: Submission): List<Violation> {
        val table = submission.profile.publications.name
        val scope = submission.publications.associate { it.publicationId to it[Publications.IN_SCOPE] }

        val decisions = submission.publications.mapNotNull { publication ->
            val inScope = publication[Publications.IN_SCOPE]
            val reason = publication[Publications.EXCLUSION_REASON]
            when {
                inScope == null -> Violation.error(table, publication, "не заполнен ${Publications.IN_SCOPE}")
                inScope == "false" && reason == null -> Violation.error(table, publication, "статья исключена без ${Publications.EXCLUSION_REASON}")
                inScope == "true" && reason != null -> Violation.error(table, publication, "${Publications.EXCLUSION_REASON} у статьи в области")
                else -> null
            }
        }
        val years = submission.publications.filter { it[Publications.YEAR] != null && (it.integer(Publications.YEAR) ?: 0) < 1 }
            .map { Violation.error(table, it, "${Publications.YEAR} = «${it[Publications.YEAR]}» — не год") }

        val dependent = submission.profile.allTables.filter { it.name != table }.flatMap { spec ->
            submission.rows(spec).map { it.publicationId }.distinct().mapNotNull { publication ->
                when {
                    publication !in scope -> Violation.error(spec.name, publication, null, "статьи нет в $table")
                    scope[publication] == "false" -> Violation.error(spec.name, publication, null, "строки у исключённой статьи")
                    else -> null
                }
            }
        }
        return decisions + years + dependent
    }
}
