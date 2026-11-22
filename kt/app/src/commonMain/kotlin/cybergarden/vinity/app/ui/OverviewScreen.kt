package cybergarden.vinity.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cybergarden.vinity.api.ChartDto
import cybergarden.vinity.api.ChartKindDto
import cybergarden.vinity.app.AppStore

@Composable
fun OverviewScreen(store: AppStore) {
    val set = store.set
    val overview = store.overview
    val loading = store.loading
    val subtitle = listOfNotNull(
        store.profile?.title,
        set?.let { "набор ${setTitle(it.ref, it.uploads)}" },
    ).joinToString(" · ")
    ScreenList("overview-${set?.ref?.path}-${set?.source}") {
        centered("head") { PageHeader("Обзор набора", subtitle) }
        when {
            overview == null && loading -> centered("loading") { ScreenLoading() }
            overview == null || overview.charts.isEmpty() -> centered("empty") {
                EmptyState("Значений пока нет. Загрузите статьи на экране «Статьи».")
            }
            else -> overview.charts.forEachIndexed { index, chart ->
                centered("chart-$index") { ChartBlock(store, chart) }
            }
        }
    }
}

@Composable
internal fun ScreenLoading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Spinner(size = 24.dp)
    }
}

@Composable
private fun ChartBlock(store: AppStore, chart: ChartDto) {
    Block(Modifier.padding(bottom = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Bold(chart.title)
            chart.hint?.let { Muted(it, small = true) }
            when (chart.kind) {
                ChartKindDto.BARS -> Bars(store, chart)
                ChartKindDto.SCATTER -> ScatterChart(
                    points = chart.points.map { point ->
                        ScatterPoint(point.x, point.y, point.label, point.flagged, listOfNotNull(point.label, point.detail).joinToString("\n")) {
                            store.openValue(point.publicationId, point.valueId)
                        }
                    },
                    lines = chart.lines.map { ScatterLine(it.slope, it.offset, it.title, it.limit) },
                    xLabel = chart.xLabel,
                    yLabel = chart.yLabel,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Bars(store: AppStore, chart: ChartDto) {
    val bars = chart.bars
    if (bars.isEmpty()) return
    val ticks = niceTicks(0.0, bars.maxOf { it.value }.coerceAtLeast(0.0), 4)
    val percent = chart.unit == "%"
    val normal = C.c1
    val flagged = C.err
    HBarChart(
        rows = bars.map { bar ->
            val article = bar.publicationId
            val value = bar.valueId
            BarRow(
                label = bar.label,
                value = bar.value,
                text = bar.text,
                color = if (bar.flagged) flagged else normal,
                tip = listOfNotNull(bar.label, bar.text, bar.detail).joinToString("\n"),
                onClick = if (article != null && value != null) {
                    { store.openValue(article, value) }
                } else {
                    null
                },
            )
        },
        max = ticks.last(),
        ticks = ticks,
        modifier = Modifier.padding(top = 8.dp),
        tickLabel = { if (percent) "${number(it)} %" else number(it) },
        labelWidth = 240.dp,
    )
    if (bars.any { it.flagged }) {
        ChartLegend(listOf("значение" to normal, "к значению есть замечание" to flagged), Modifier.padding(top = 4.dp))
    }
}
