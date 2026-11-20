package cybergarden.vinity.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cybergarden.vinity.domain.model.Issue
import cybergarden.vinity.domain.model.MetricResult
import cybergarden.vinity.domain.model.ResultOrigin

@Composable
fun ViewerScreen(state: ViewerState) {
    Column(Modifier.fillMaxSize()) {
        TopBar(state)
        HorizontalDivider()
        Row(Modifier.fillMaxSize()) {
            PublicationList(state, Modifier.width(220.dp).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight()) {
                ResultList(state, Modifier.weight(2f))
                HorizontalDivider()
                IssueList(state, Modifier.weight(1f))
            }
            PagePreview(state, Modifier.weight(1.2f).fillMaxHeight())
        }
    }
}

@Composable
private fun TopBar(state: ViewerState) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ViewerState.DATASETS.forEach { name ->
            FilterChip(selected = state.dataset == name, onClick = { state.open(name) }, label = { Text(name) })
        }
        val s = state.submission
        Text(
            "статей ${s.publications.size} · приборов ${s.devices.size} · значений ${s.results.size} · замечаний ${s.issues.size}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PublicationList(state: ViewerState, modifier: Modifier) {
    LazyColumn(modifier) {
        items(state.submission.publications) { publication ->
            val selected = publication == state.selectedPublication
            Column(
                Modifier.fillMaxWidth()
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable { state.selectPublication(publication) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(publication.publicationId, fontWeight = FontWeight.SemiBold)
                Text(
                    publication.title.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (publication.inScope == false) {
                    Text("вне области", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ResultList(state: ViewerState, modifier: Modifier) {
    LazyColumn(modifier) {
        items(state.results) { result ->
            val selected = result == state.selectedResult
            Row(
                Modifier.fillMaxWidth()
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable { state.selectResult(result) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(result.metricName, Modifier.width(110.dp), fontWeight = FontWeight.SemiBold)
                Text(result.valueLabel(), Modifier.width(120.dp))
                Text(result.contextLabel(), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun IssueList(state: ViewerState, modifier: Modifier) {
    LazyColumn(modifier) {
        items(state.issues) { issue -> IssueRow(issue) { state.selectIssue(issue) } }
    }
}

@Composable
private fun IssueRow(issue: Issue, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(issue.checkName, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        Text(issue.description.orEmpty(), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PagePreview(state: ViewerState, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.TopCenter) {
        val page = state.page
        if (page == null) {
            Text("Выберите значение или замечание", Modifier.padding(24.dp))
        } else {
            Image(
                bitmap = page,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            )
        }
    }
}

private fun MetricResult.valueLabel(): String {
    val value = normalizedValue?.let { "%s %s".format(it, normalizedUnit.orEmpty()) } ?: "—"
    return if (spreadValue != null) "$value ± $spreadValue" else value
}

private fun MetricResult.contextLabel(): String = listOfNotNull(
    if (origin == ResultOrigin.CITED) "цит. ${citedPublicationId.orEmpty()}" else deviceId,
    aggregation?.code,
    measurementId,
    source.page?.let { "стр. $it" },
    source.table ?: source.figure,
    duplicateOf?.let { "повтор $it" },
    source.fragment,
).joinToString(" · ")
