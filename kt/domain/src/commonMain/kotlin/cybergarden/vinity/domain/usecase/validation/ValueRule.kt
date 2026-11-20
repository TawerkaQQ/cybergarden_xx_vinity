package cybergarden.vinity.domain.usecase.validation

import cybergarden.vinity.domain.model.Origin
import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.model.Severity
import cybergarden.vinity.domain.model.Status
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.ValuesSpec
import cybergarden.vinity.domain.model.Violation

class ValueRule : SubmissionRule {

    override fun check(submission: Submission): List<Violation> {
        val spec = submission.profile.values
        return submission.values.flatMap { row ->
            problems(spec, row).map { (severity, message) -> Violation(severity, spec.table, row.publicationId, row.id, message) }
        }
    }

    private fun problems(spec: ValuesSpec, row: Record): List<Pair<Severity, String>> = buildList {
        fun error(message: String) = add(Severity.ERROR to message)
        fun warning(message: String) = add(Severity.WARNING to message)

        val status = row[spec.status]
        val origin = row[spec.origin]
        val value = row[spec.value]
        val page = row[spec.page]

        if (row[spec.metric] == null) error("пустой ${spec.metric}")
        if (status == null) error("пустой ${spec.status}")
        if (origin == null) error("пустой ${spec.origin}")
        if (origin == Origin.CITED && row[spec.cited] == null) error("$origin без ${spec.cited}")
        if (origin == Origin.PRIMARY && row[spec.cited] != null) error("$origin с заполненным ${spec.cited}")
        if (value != null && row.number(spec.value) == null) error("${spec.value} = «$value» — не число")
        if (page != null && (row.integer(spec.page) ?: 0) < 1) error("${spec.page} = «$page» — не номер страницы")

        if (status == Status.EXTRACTED) {
            if (row[spec.rawValue] == null) error("$status без ${spec.rawValue}")
            if (value == null) warning("$status без ${spec.value}")
            if (page == null) error("$status без ${spec.page}")
            if (listOf(spec.fragment, spec.sourceTable, spec.figure).all { row[it] == null }) {
                error("$status без цитаты, таблицы и рисунка")
            }
        } else if (value != null) {
            warning("${spec.status} = «${status.orEmpty()}», но ${spec.value} заполнено")
        }

        spec.mirrors.forEach { (copy, source) ->
            if (!sameCell(row[copy], row[source])) {
                error("$copy = «${row[copy].orEmpty()}», а $source = «${row[source].orEmpty()}»")
            }
        }

        val target = spec.metrics[row[spec.metric]]
        if (target != null && value != null && row[spec.unit] != target) {
            error("${row[spec.metric]} в «${row[spec.unit].orEmpty()}», нужно $target")
        }
    }

    private fun sameCell(left: String?, right: String?): Boolean {
        val x = left?.toDoubleOrNull()
        val y = right?.toDoubleOrNull()
        return if (x != null && y != null) x == y else left == right
    }
}
