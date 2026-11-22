package cybergarden.vinity.domain.usecase

import cybergarden.vinity.domain.model.DecimalText
import cybergarden.vinity.domain.model.IssueVerdict
import cybergarden.vinity.domain.model.Issues
import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.port.TextNormalizer

data class QuoteQuality(
    val values: Int,
    val found: Int,
    val textValues: Int,
    val textFound: Int,
    val withoutPageText: Int,
    val byVariant: List<Tally>,
    val missing: List<ValueFacts>,
)

data class Conversion(val from: String, val to: String, val count: Int, val example: Record)

data class MetricUnits(val metric: String, val unit: String?, val values: Int, val inUnit: Int)

data class Normalization(
    val values: Int,
    val converted: Int,
    val conversions: List<Conversion>,
    val metrics: List<MetricUnits>,
    val vocabularyChecked: Int,
    val vocabularyValid: Int,
    val aliases: List<Triple<String, String, Int>>,
)

data class CheckCount(val check: String, val found: Int, val gold: Int?, val matched: Int?)

data class GoldIssues(val matched: Int, val goldTotal: Int, val onlyOurs: List<Record>, val missed: List<Record>)

data class ChecksQuality(
    val issues: Int,
    val confirmed: Int,
    val dismissed: Int,
    val byCheck: List<CheckCount>,
    val gold: GoldIssues?,
)

data class Quality(val quotes: QuoteQuality, val normalization: Normalization, val checks: ChecksQuality)

class AssessQuality(private val normalizer: TextNormalizer) {

    operator fun invoke(catalog: ValueCatalog, gold: Submission?): Quality =
        Quality(quotes(catalog), normalization(catalog), checks(catalog, gold))

    private fun quotes(catalog: ValueCatalog): QuoteQuality {
        val facts = catalog.facts
        val text = facts.filter { it.kind != ValueKind.FIGURE }
        val variants = facts.filter { it.quote.status == QuoteStatus.FOUND }.groupingBy { it.quote.variant.orEmpty() }.eachCount()
            .map { Tally(it.key, it.value) }
            .sortedByDescending { it.count }
        val rowLabels = facts.count { it.quote.status == QuoteStatus.ROW_LABEL }
        return QuoteQuality(
            values = facts.size,
            found = facts.count { it.quote.found },
            textValues = text.size,
            textFound = text.count { it.quote.found },
            withoutPageText = facts.count { it.quote.status == QuoteStatus.NO_PAGE_TEXT },
            byVariant = variants + listOfNotNull(Tally(ROW_LABEL, rowLabels).takeIf { rowLabels > 0 }),
            missing = facts.filterNot { it.quote.found }.sortedBy { it.kind == ValueKind.FIGURE }.take(MISSING_LIMIT),
        )
    }

    private fun normalization(catalog: ValueCatalog): Normalization {
        val profile = catalog.index.profile
        val spec = profile.values
        val rows = catalog.index.allValues
        val changed = rows.filter { row ->
            val rawUnit = row[spec.rawUnit]
            val unitChanged = rawUnit != null && rawUnit != row[spec.unit]
            val raw = row[spec.rawValue]
            val value = row[spec.value]
            val numberChanged = raw?.toDoubleOrNull() != null && value != null && !DecimalText.sameAtReportedPrecision(raw, value)
            unitChanged || numberChanged
        }
        val conversions = changed.groupBy { it[spec.rawUnit].orEmpty() to it[spec.unit].orEmpty() }
            .map { (units, group) -> Conversion(units.first, units.second, group.size, group.first()) }
            .sortedByDescending { it.count }
        val metrics = rows.groupBy { it[spec.metric].orEmpty() }.filterKeys { it.isNotEmpty() }.map { (metric, group) ->
            val target = spec.metrics[metric] ?: group.mapNotNull { it[spec.unit] }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            MetricUnits(metric, target, group.size, group.count { it[spec.unit] == target })
        }.sortedByDescending { it.values }.take(METRICS_LIMIT)
        val cells = profile.tables.flatMap { table ->
            table.vocabularies.flatMap { (column, allowed) ->
                catalog.index.submission.rows(table).flatMap { row ->
                    val values = if (column in table.lists) row.list(column) else listOfNotNull(row[column])
                    values.map { it in allowed }
                }
            }
        }
        val written = rows.mapNotNull { it[spec.rawUnit] }.groupingBy { it }.eachCount()
        val aliases = profile.units.aliases.mapNotNull { (alias, canonical) ->
            written[alias]?.let { Triple(alias, canonical, it) }
        }.sortedByDescending { it.third }
        return Normalization(
            values = rows.size,
            converted = changed.size,
            conversions = conversions,
            metrics = metrics,
            vocabularyChecked = cells.size,
            vocabularyValid = cells.count { it },
            aliases = aliases,
        )
    }

    private fun checks(catalog: ValueCatalog, gold: Submission?): ChecksQuality {
        val issues = catalog.index.allIssues
        val reviews = catalog.reviews
        val goldIssues = gold?.issues
        val matching = goldIssues?.let { IssueMatcher(normalizer).match(issues, it) }
        val found = issues.groupingBy { it[Issues.CHECK].orEmpty() }.eachCount()
        val goldFound = goldIssues?.groupingBy { it[Issues.CHECK].orEmpty() }?.eachCount()
        val matched = matching?.pairs?.groupingBy { it.first[Issues.CHECK].orEmpty() }?.eachCount()
        val profileChecks = catalog.index.profile.checks
        val others = (found.keys + goldFound?.keys.orEmpty()).filterNot { it.isEmpty() || it in profileChecks }
            .sortedByDescending { (found[it] ?: 0) + (goldFound?.get(it) ?: 0) }
        return ChecksQuality(
            issues = issues.size,
            confirmed = issues.count { reviews.issue(it)?.verdict == IssueVerdict.CONFIRMED },
            dismissed = issues.count { reviews.issue(it)?.verdict == IssueVerdict.DISMISSED },
            byCheck = (profileChecks + others).map { check ->
                CheckCount(check, found[check] ?: 0, goldFound?.let { it[check] ?: 0 }, matched?.let { it[check] ?: 0 })
            },
            gold = if (matching != null && goldIssues != null) {
                GoldIssues(matching.pairs.size, goldIssues.size, matching.unmatchedCandidates, matching.unmatchedGold)
            } else {
                null
            },
        )
    }

    companion object {
        const val ROW_LABEL = "row_label"
        private const val MISSING_LIMIT = 300
        private const val METRICS_LIMIT = 60
    }
}
