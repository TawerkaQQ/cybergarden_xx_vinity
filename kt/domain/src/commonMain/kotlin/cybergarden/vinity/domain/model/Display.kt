package cybergarden.vinity.domain.model

data class DisplaySpec(
    val record: RecordDisplay? = null,
    val recordsTab: String? = null,
    val metrics: Map<String, MetricDisplay> = emptyMap(),
    val checks: Map<String, CheckDisplay> = emptyMap(),
    val aggregations: Map<String, String> = emptyMap(),
    val overview: List<ChartSpec> = emptyList(),
) {
    fun references(): List<String> = listOfNotNull(record?.column) + overview.mapNotNull {
        when (it) {
            is ChartSpec.Scatter -> it.x.column
            is ChartSpec.Leaders -> it.group
            else -> null
        }
    }
}

data class RecordDisplay(val column: String, val title: String?, val group: String?)

data class MetricDisplay(val title: String?, val explain: String?)

data class CheckDisplay(val title: String?, val rule: String?)

data class LinkedField(val column: String, val field: String)

data class ChartLine(val slope: Double, val offset: Double, val title: String, val limit: Boolean)

sealed interface ChartSpec {
    val title: String
    val hint: String?

    data class Scatter(
        override val title: String,
        override val hint: String?,
        val y: String,
        val x: LinkedField,
        val xLabel: String?,
        val yLabel: String?,
        val lines: List<ChartLine>,
    ) : ChartSpec

    data class Best(
        override val title: String,
        override val hint: String?,
        val metric: String,
        val aggregation: String?,
    ) : ChartSpec

    data class Leaders(
        override val title: String,
        override val hint: String?,
        val group: String,
        val metrics: List<String>,
    ) : ChartSpec

    data class Counts(
        override val title: String,
        override val hint: String?,
    ) : ChartSpec
}
