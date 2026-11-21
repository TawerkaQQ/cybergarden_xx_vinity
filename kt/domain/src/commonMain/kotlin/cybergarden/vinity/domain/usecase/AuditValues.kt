package cybergarden.vinity.domain.usecase

import cybergarden.vinity.domain.model.CheckRule
import cybergarden.vinity.domain.model.DecimalText
import cybergarden.vinity.domain.model.Issues
import cybergarden.vinity.domain.model.Profile
import cybergarden.vinity.domain.model.Publications
import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.model.Role
import cybergarden.vinity.domain.model.Status
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.ValuesSpec
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round

class Audit(val submission: Submission, val duplicates: Int, val issues: List<Record>)

class AuditValues {

    private class Found(val publicationId: String, val records: List<Record>, val check: String, val description: String)

    private class Marked(val rows: List<Record>, val count: Int)

    operator fun invoke(submission: Submission): Audit {
        val profile = submission.profile
        val spec = profile.values
        val marked = markDuplicates(submission.values, spec)
        val found = profile.rules.flatMap { rule ->
            when (rule.rule) {
                CONTRADICTION -> contradictions(marked.rows, profile, rule.name)
                DUPLICATE_CONSISTENCY -> inconsistentDuplicates(marked.rows, spec, rule.name)
                METRIC_SCALE -> outOfScale(marked.rows, spec, rule)
                METRIC_K -> fallingWithK(marked.rows, spec, rule)
                PERCENT_VS_POINTS -> percentPoints(marked.rows, spec, rule)
                else -> emptyList()
            }
        }
        val existing = submission.issues
        val covered = existing.map { it.publicationId to it.list(Issues.RECORDS).toSet() }
        val fresh = found.filterNot { candidate ->
            val ids = candidate.records.map { it.id }.toSet()
            covered.any { (publication, records) -> publication == candidate.publicationId && records.containsAll(ids) }
        }
        val issues = numbered(fresh, existing, spec)
        val tables = submission.tables + (spec.table to marked.rows) + (profile.issues.name to existing + issues)
        return Audit(submission.copy(tables = tables), marked.count, issues)
    }

    private fun markDuplicates(rows: List<Record>, spec: ValuesSpec): Marked {
        val keyColumns = spec.duplicateKey.map(spec::column)
        val primaries = mutableMapOf<RowKey, Record>()
        rows.filter { it[spec.value] != null }
            .groupBy { row -> RowKey(row.publicationId, keyColumns.map { column -> cell(row, column, spec) }) }
            .forEach { (key, group) ->
                val primary = group.firstOrNull { it[spec.duplicateOf] == null && group.any { other -> other[spec.duplicateOf] == it.id } }
                    ?: group.firstOrNull { it[spec.duplicateOf] == null && (it[spec.sourceTable] != null || it[spec.figure] != null) }
                    ?: group.firstOrNull { it[spec.duplicateOf] == null }
                primary?.let { primaries[key] = it }
            }
        var count = 0
        val marked = rows.map { row ->
            if (row[spec.duplicateOf] != null || row[spec.value] == null) return@map row
            val primary = primaries[RowKey(row.publicationId, keyColumns.map { cell(row, it, spec) })]
            if (primary == null || primary.id == row.id) return@map row
            count++
            row.copy(cells = row.cells + (spec.duplicateOf to primary.id))
        }
        return Marked(marked, count)
    }

    private data class RowKey(val publicationId: String, val cells: List<String>)

    private fun cell(row: Record, column: String, spec: ValuesSpec): String =
        if (column == spec.value) row.number(column)?.toString() ?: row[column].orEmpty() else row[column].orEmpty()

    private fun contradictions(rows: List<Record>, profile: Profile, check: String): List<Found> {
        val spec = profile.values
        val identity = spec.duplicateKey.filterNot { it == Role.METRIC || it == Role.VALUE }.map(spec::column)
        val references = profile.valueTable.references.keys - spec.duplicateOf
        return rows.asSequence()
            .filter { eligible(it, spec) && exact(it, spec) && references.any { column -> it[column] != null } }
            .filter { spec.metrics.isEmpty() || it[spec.metric] in spec.metrics }
            .groupBy { row -> listOf(row.publicationId, row[spec.metric], row[spec.unit]) + identity.map { row[it] } }
            .values
            .mapNotNull { group -> contradiction(group, spec, check) }
    }

    private fun contradiction(group: List<Record>, spec: ValuesSpec, check: String): Found? {
        val distinct = group.distinctBy { it.number(spec.value) }
        val places = distinct.map { it[spec.sourceTable].orEmpty() }.toSet()
        if (distinct.size < 2 || (places.size == 1 && places.single().isNotEmpty())) return null
        val low = distinct.minBy { it.number(spec.value) ?: 0.0 }
        val high = distinct.maxBy { it.number(spec.value) ?: 0.0 }
        val lowText = low[spec.value].orEmpty()
        val highText = high[spec.value].orEmpty()
        if ((high.number(spec.value) ?: 0.0) - (low.number(spec.value) ?: 0.0) <= rounding(lowText, highText)) return null
        val mentions = distinct.joinToString("; ") { row -> "${row[spec.value]} (с. ${row[spec.page].orEmpty()})" }
        val unit = low[spec.unit]?.let { " $it" }.orEmpty()
        return Found(low.publicationId, distinct, check, "${low[spec.metric]} одной записи в разных местах статьи: $mentions$unit")
    }

    private fun inconsistentDuplicates(rows: List<Record>, spec: ValuesSpec, check: String): List<Found> {
        val byId = rows.associateBy { it.publicationId to it.id }
        val keyColumns = spec.duplicateKey.map(spec::column)
        return rows.mapNotNull { row ->
            val primary = row[spec.duplicateOf]?.let { byId[row.publicationId to it] } ?: return@mapNotNull null
            if (primary.id == row.id) return@mapNotNull null
            val differing = keyColumns.filterNot { column -> same(row[column], primary[column], column == spec.value) }
            if (differing.isEmpty()) return@mapNotNull null
            Found(row.publicationId, listOf(row, primary), check, "Повтор ${row.id} расходится с основной записью ${primary.id}: ${differing.joinToString()}")
        }
    }

    private fun outOfScale(rows: List<Record>, spec: ValuesSpec, rule: CheckRule): List<Found> = rows.mapNotNull { row ->
        val unit = row[spec.unit] ?: return@mapNotNull null
        val low = rule.number(unit, 0) ?: return@mapNotNull null
        val high = rule.number(unit, 1) ?: return@mapNotNull null
        if (row[spec.duplicateOf] != null || !measured(row, spec) || signed(row, spec)) return@mapNotNull null
        val value = row.number(spec.value) ?: return@mapNotNull null
        if (value in low..high) return@mapNotNull null
        Found(row.publicationId, listOf(row), rule.name, "${row[spec.metric]} = ${row[spec.value]} $unit вне шкалы ${plain(low)}–${plain(high)} $unit")
    }

    private fun fallingWithK(rows: List<Record>, spec: ValuesSpec, rule: CheckRule): List<Found> {
        val column = rule.list(PARAM_COLUMN).firstOrNull() ?: return emptyList()
        val patterns = rule.list(PARAM_METRICS).map { it.lowercase() }
        val identity = spec.duplicateKey.map(spec::column).filterNot { it == spec.metric || it == spec.value || it == column }
        return rows.asSequence()
            .filter { row -> row[spec.duplicateOf] == null && measured(row, spec) && row.number(spec.value) != null && row.number(column) != null }
            .filter { row -> patterns.any { pattern -> row[spec.metric].orEmpty().lowercase().contains(pattern) } }
            .groupBy { row -> listOf(row.publicationId, baseMetric(row, spec, column)) + identity.map { row[it] } }
            .values
            .flatMap { group ->
                val byK = group.groupBy { it.number(column) ?: 0.0 }.entries.sortedBy { it.key }.map { it.value.first() }
                byK.zipWithNext().mapNotNull { (smaller, larger) -> falling(smaller, larger, spec, column, rule.name) }
            }
    }

    private fun falling(smaller: Record, larger: Record, spec: ValuesSpec, column: String, check: String): Found? {
        val before = smaller.number(spec.value) ?: return null
        val after = larger.number(spec.value) ?: return null
        if (before - after <= rounding(smaller[spec.value].orEmpty(), larger[spec.value].orEmpty())) return null
        val metric = baseMetric(smaller, spec, column)
        return Found(
            smaller.publicationId,
            listOf(smaller, larger),
            check,
            "$metric при k = ${smaller[column]} равно ${smaller[spec.value]}, а при k = ${larger[column]} — ${larger[spec.value]}: с ростом k такая метрика не уменьшается",
        )
    }

    private fun percentPoints(rows: List<Record>, spec: ValuesSpec, rule: CheckRule): List<Found> {
        val unit = rule.list(PARAM_UNIT).firstOrNull() ?: return emptyList()
        val words = rule.list(PARAM_METRICS).map { it.lowercase() }
        val by = rule.list(PARAM_BY)
        val scored = rows.filter { it[spec.duplicateOf] == null && measured(it, spec) && it[spec.unit] == unit && it.number(spec.value) != null }
        val pools = scored.groupBy { row -> listOf(row.publicationId) + by.map { row[it] } }
        return scored
            .filter { row -> row[spec.rawValue].orEmpty().trimEnd().endsWith(unit) && words.any { row[spec.metric].orEmpty().lowercase().contains(it) } }
            .mapNotNull { change -> pointsPair(change, pools[listOf(change.publicationId) + by.map { change[it] }].orEmpty(), spec, rule.name) }
    }

    private fun pointsPair(change: Record, pool: List<Record>, spec: ValuesSpec, check: String): Found? {
        val reported = abs(change.number(spec.value) ?: return null)
        val metric = change[spec.metric].orEmpty().lowercase()
        val tolerance = rounding(change[spec.value].orEmpty(), change[spec.value].orEmpty())
        val bases = pool.filter { other ->
            val base = other[spec.metric]?.lowercase()
            other.id != change.id && base != null && base != metric && metric.contains(base)
        }
        for (first in bases) {
            for (second in bases) {
                val high = first.number(spec.value) ?: continue
                val low = second.number(spec.value) ?: continue
                if (high <= low || low == 0.0) continue
                val points = high - low
                val relative = points / low * PERCENT
                if (abs(points - reported) <= tolerance && abs(relative - reported) > tolerance) {
                    return Found(
                        change.publicationId,
                        listOf(change, first, second),
                        check,
                        "${change[spec.metric]} «${change[spec.rawValue]}» совпадает с разностью ${first[spec.value]} и ${second[spec.value]}: " +
                            "это процентные пункты, относительное изменение было бы ${plain(relative)} %",
                    )
                }
            }
        }
        return null
    }

    private fun numbered(found: List<Found>, existing: List<Record>, spec: ValuesSpec): List<Record> {
        val taken = existing.map { it.publicationId to it.id }.toMutableSet()
        val counters = mutableMapOf<String, Int>()
        return found.map { issue ->
            val id = generateSequence {
                val next = (counters[issue.publicationId] ?: 0) + 1
                counters[issue.publicationId] = next
                "$PREFIX$next"
            }.first { taken.add(issue.publicationId to it) }
            val source = issue.records.first()
            Record(
                issue.publicationId,
                id,
                mapOf(
                    Publications.ID to issue.publicationId,
                    Issues.ID to id,
                    Issues.RECORDS to issue.records.joinToString(";") { it.id },
                    Issues.CHECK to issue.check,
                    Issues.DESCRIPTION to issue.description,
                    Issues.PAGE to source[spec.page].orEmpty(),
                    Issues.FRAGMENT to source[spec.fragment].orEmpty(),
                ),
            )
        }
    }

    private fun baseMetric(row: Record, spec: ValuesSpec, column: String): String =
        row[spec.metric].orEmpty().replace("@${row[column].orEmpty()}", "").trim()

    private fun eligible(row: Record, spec: ValuesSpec): Boolean =
        row.number(spec.value) != null && (row[spec.status] ?: Status.EXTRACTED) == Status.EXTRACTED && row[spec.cited] == null

    private fun measured(row: Record, spec: ValuesSpec): Boolean =
        (row[spec.status] ?: Status.EXTRACTED) == Status.EXTRACTED && exact(row, spec)

    private fun exact(row: Record, spec: ValuesSpec): Boolean = row[spec.rawValue]?.trimStart()?.firstOrNull()?.let { it !in INEXACT } ?: true

    private fun signed(row: Record, spec: ValuesSpec): Boolean = row[spec.rawValue]?.trimStart()?.firstOrNull()?.let { it in SIGNS } == true

    private fun same(left: String?, right: String?, numeric: Boolean): Boolean =
        if (numeric && left != null && right != null) DecimalText.sameAtReportedPrecision(left, right) else left == right

    private fun rounding(first: String, second: String): Double =
        0.5 * 10.0.pow(-minOf(decimals(first), decimals(second))) + ROUNDING_SLACK

    private fun decimals(text: String): Int = text.substringAfter('.', "").length

    private fun plain(number: Double): String =
        if (number == floor(number)) number.toLong().toString() else (round(number * PRECISION) / PRECISION).toString()

    companion object {
        const val CONTRADICTION = "contradiction"
        const val DUPLICATE_CONSISTENCY = "duplicate_consistency"
        const val METRIC_SCALE = "metric_scale"
        const val METRIC_K = "metric_k"
        const val PERCENT_VS_POINTS = "percent_vs_points"
        const val PREFIX = "S"
        private const val PARAM_COLUMN = "column"
        private const val PARAM_METRICS = "metrics"
        private const val PARAM_UNIT = "unit"
        private const val PARAM_BY = "by"
        private const val INEXACT = "<>≤≥~≈"
        private const val SIGNS = "+-−±"
        private const val PERCENT = 100.0
        private const val PRECISION = 100.0
        private const val ROUNDING_SLACK = 1e-9
    }
}
