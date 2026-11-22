package cybergarden.vinity.domain.usecase

import cybergarden.vinity.domain.model.ChartLine
import cybergarden.vinity.domain.model.ChartSpec
import cybergarden.vinity.domain.model.Record

enum class ChartKind { BARS, SCATTER }

data class Bar(
    val label: String,
    val value: Double,
    val text: String,
    val publicationId: String?,
    val valueId: String?,
    val flagged: Boolean,
    val detail: String?,
)

data class Point(
    val x: Double,
    val y: Double,
    val label: String,
    val publicationId: String,
    val valueId: String,
    val flagged: Boolean,
    val detail: String?,
)

data class Chart(
    val kind: ChartKind,
    val title: String,
    val hint: String?,
    val xLabel: String? = null,
    val yLabel: String? = null,
    val unit: String? = null,
    val bars: List<Bar> = emptyList(),
    val points: List<Point> = emptyList(),
    val lines: List<ChartLine> = emptyList(),
)

class BuildOverview {

    operator fun invoke(catalog: ValueCatalog): List<Chart> {
        val primary = catalog.facts.filter { it.row[catalog.index.profile.values.duplicateOf] == null }
        return catalog.index.profile.display.overview.flatMap { spec ->
            when (spec) {
                is ChartSpec.Scatter -> listOfNotNull(scatter(catalog, primary, spec))
                is ChartSpec.Best -> listOfNotNull(best(catalog, primary, spec))
                is ChartSpec.Leaders -> leaders(catalog, primary, spec)
                is ChartSpec.Counts -> listOfNotNull(counts(catalog, spec))
            }
        }
    }

    private fun scatter(catalog: ValueCatalog, facts: List<ValueFacts>, spec: ChartSpec.Scatter): Chart? {
        val values = catalog.index.profile.values
        val points = facts.filter { it.row[values.metric] == spec.y }.mapNotNull { fact ->
            val y = number(catalog, fact.row, spec.y) ?: return@mapNotNull null
            val xText = catalog.index.linkedField(fact.row, spec.x.column, spec.x.field) ?: return@mapNotNull null
            val x = xText.toDoubleOrNull() ?: return@mapNotNull null
            Point(
                x = x,
                y = y,
                label = label(fact),
                publicationId = fact.row.publicationId,
                valueId = fact.row.id,
                flagged = spec.lines.any { it.limit && y > it.slope * x + it.offset },
                detail = "${spec.yLabel ?: spec.y}: ${amount(catalog, fact.row)} · ${spec.xLabel ?: spec.x.field}: $xText",
            )
        }
        if (points.isEmpty()) return null
        return Chart(ChartKind.SCATTER, spec.title, spec.hint, spec.xLabel, spec.yLabel, points = points, lines = spec.lines)
    }

    private fun best(catalog: ValueCatalog, facts: List<ValueFacts>, spec: ChartSpec.Best): Chart? {
        val values = catalog.index.profile.values
        val numbered = commonUnit(
            catalog,
            facts.filter { it.row[values.metric] == spec.metric && (spec.aggregation == null || it.row[values.aggregation] in setOf(null, spec.aggregation)) }
                .mapNotNull { fact -> number(catalog, fact.row, spec.metric)?.let { fact to it } },
        )
        val bars = numbered
            .groupBy { it.first.row.publicationId }
            .map { (_, group) -> group.maxBy { it.second } }
            .sortedByDescending { it.second }
            .map { (fact, number) -> bar(catalog, fact, number, place(catalog, fact.row)) }
        if (bars.isEmpty()) return null
        return Chart(ChartKind.BARS, spec.title, spec.hint, unit = numbered.first().first.row[values.unit], bars = bars)
    }

    private fun leaders(catalog: ValueCatalog, facts: List<ValueFacts>, spec: ChartSpec.Leaders): List<Chart> {
        val values = catalog.index.profile.values
        val titles = catalog.index.profile.display.metrics
        return facts.mapNotNull { fact -> catalog.index.linkedRecord(fact.row, spec.group)?.let { it.label to fact } }
            .groupBy({ it.first }, { it.second })
            .entries.sortedByDescending { it.value.size }
            .take(GROUPS)
            .mapNotNull { (group, members) ->
                val numbered = spec.metrics.asSequence().map { metric ->
                    metric to commonUnit(catalog, members.filter { it.row[values.metric] == metric }.mapNotNull { fact -> number(catalog, fact.row, metric)?.let { fact to it } })
                }.firstOrNull { it.second.size > 1 } ?: return@mapNotNull null
                val (metric, ranked) = numbered
                Chart(
                    kind = ChartKind.BARS,
                    title = group,
                    hint = listOfNotNull("${titles[metric]?.title ?: metric} ($metric)", spec.hint).joinToString(" · "),
                    unit = ranked.first().first.row[values.unit],
                    bars = ranked.sortedByDescending { it.second }.take(BARS).map { (fact, number) ->
                        bar(catalog, fact, number, place(catalog, fact.row))
                    },
                )
            }
    }

    private fun counts(catalog: ValueCatalog, spec: ChartSpec.Counts): Chart? {
        val bars = catalog.metrics.filter { it.name.isNotEmpty() }.take(COUNTS).map {
            Bar(it.name, it.count.toDouble(), it.count.toString(), null, null, false, null)
        }
        if (bars.isEmpty()) return null
        return Chart(ChartKind.BARS, spec.title, spec.hint, bars = bars)
    }

    private fun bar(catalog: ValueCatalog, fact: ValueFacts, number: Double, detail: String) = Bar(
        label = label(fact),
        value = number,
        text = amount(catalog, fact.row),
        publicationId = fact.row.publicationId,
        valueId = fact.row.id,
        flagged = fact.activeIssues > 0,
        detail = detail,
    )

    private fun commonUnit(catalog: ValueCatalog, numbered: List<Pair<ValueFacts, Double>>): List<Pair<ValueFacts, Double>> {
        val unit = catalog.index.profile.values.unit
        val common = numbered.groupingBy { it.first.row[unit] }.eachCount().maxByOrNull { it.value }?.key
        return numbered.filter { it.first.row[unit] == common }
    }

    private fun number(catalog: ValueCatalog, row: Record, metric: String): Double? {
        val number = row.number(catalog.index.profile.values.value) ?: return null
        val range = catalog.index.profile.ranges[metric]
        return number.takeIf { range == null || it in range }
    }

    private fun label(fact: ValueFacts): String = "${fact.row.publicationId} · ${fact.record?.label ?: fact.row.id}"

    private fun amount(catalog: ValueCatalog, row: Record): String {
        val values = catalog.index.profile.values
        return listOfNotNull(row[values.value], row[values.unit]).joinToString(" ")
    }

    private fun place(catalog: ValueCatalog, row: Record): String {
        val values = catalog.index.profile.values
        return listOfNotNull(row[values.page]?.let { "стр. $it" }, row[values.sourceTable] ?: row[values.figure] ?: row[values.section])
            .joinToString(", ")
    }

    private companion object {
        const val GROUPS = 8
        const val BARS = 12
        const val COUNTS = 20
    }
}
