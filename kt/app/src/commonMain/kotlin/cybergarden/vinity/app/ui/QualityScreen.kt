package cybergarden.vinity.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cybergarden.vinity.api.CountsDto
import cybergarden.vinity.api.ScoreDto
import cybergarden.vinity.app.ViewerStore
import kotlin.math.roundToInt

@Composable
fun QualityScreen(store: ViewerStore) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Точность и полнота против эталона", style = MaterialTheme.typography.titleLarge)
            FilterChip(selected = store.strict, onClick = store::toggleStrict, label = { Text("строгий режим") })
        }
        val score = store.score
        if (score == null) {
            Text("Для набора «${store.dataset?.name.orEmpty()}» нет эталона.", Modifier.padding(top = 16.dp))
        } else {
            ScoreTable(score)
        }
    }
}

@Composable
private fun ScoreTable(score: ScoreDto) {
    Text(
        "Статей оценено: ${score.publicationsScored} из ${score.publicationsInGold}. In_scope совпал: ${score.scopeAgreed} из ${score.scopeTotal}.",
        Modifier.padding(vertical = 12.dp),
    )
    HeaderRow("Срез")
    CountsRow("все упоминания", score.allMentions, emphasized = true)
    CountsRow("основные записи", score.primaryRecords, emphasized = true)
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    HeaderRow("Показатель")
    score.byMetric.forEach { (metric, counts) -> CountsRow(metric, counts) }
}

@Composable
private fun HeaderRow(title: String) {
    Cells(listOf(title, "P", "R", "F1", "TP", "FP", "FN"), FontWeight.SemiBold)
}

@Composable
private fun CountsRow(title: String, counts: CountsDto, emphasized: Boolean = false) {
    Cells(
        listOf(
            title,
            counts.precision.percent(),
            counts.recall.percent(),
            counts.f1.percent(),
            counts.truePositive.toString(),
            counts.falsePositive.toString(),
            counts.falseNegative.toString(),
        ),
        if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
private fun Cells(values: List<String>, weight: FontWeight) {
    Row(Modifier.padding(vertical = 4.dp)) {
        values.forEachIndexed { index, value ->
            Text(value, Modifier.width(if (index == 0) 180.dp else 80.dp), fontWeight = weight)
        }
    }
}

private fun Double.percent(): String {
    val tenths = (this * 1000).roundToInt()
    return "${tenths / 10}.${tenths % 10}%"
}
