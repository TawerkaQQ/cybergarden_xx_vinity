package cybergarden.vinity.app.ui

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cybergarden.vinity.api.IssueDto
import cybergarden.vinity.api.ResultDto
import cybergarden.vinity.app.ViewerStore

@Composable
fun BrowserScreen(store: ViewerStore) {
    Row(Modifier.fillMaxSize()) {
        PublicationList(store, Modifier.width(240.dp).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight()) {
            ResultList(store, Modifier.weight(2f))
            HorizontalDivider()
            IssueList(store, Modifier.weight(1f))
        }
        PagePreview(store, Modifier.weight(1.2f).fillMaxHeight())
    }
}

@Composable
private fun PublicationList(store: ViewerStore, modifier: Modifier) {
    LazyColumn(modifier) {
        items(store.dataset?.publications.orEmpty()) { publication ->
            Column(
                Modifier.fillMaxWidth()
                    .background(highlight(publication == store.selectedPublication, MaterialTheme.colorScheme.secondaryContainer))
                    .clickable { store.selectPublication(publication) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(publication.id, fontWeight = FontWeight.SemiBold)
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
private fun ResultList(store: ViewerStore, modifier: Modifier) {
    LazyColumn(modifier) {
        items(store.results) { result ->
            Row(
                Modifier.fillMaxWidth()
                    .background(highlight(result == store.selectedResult, MaterialTheme.colorScheme.primaryContainer))
                    .clickable { store.selectResult(result) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(result.metric, Modifier.width(110.dp), fontWeight = FontWeight.SemiBold)
                Text(result.valueLabel(), Modifier.width(130.dp))
                Text(result.contextLabel(), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun IssueList(store: ViewerStore, modifier: Modifier) {
    LazyColumn(modifier) {
        items(store.issues) { issue ->
            IssueRow(issue, issue == store.selectedIssue) { store.selectIssue(issue) }
        }
    }
}

@Composable
private fun IssueRow(issue: IssueDto, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(highlight(selected, MaterialTheme.colorScheme.errorContainer))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(issue.checkName, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        Text(issue.description.orEmpty(), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PagePreview(store: ViewerStore, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.TopCenter) {
        val page = store.page
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

@Composable
private fun highlight(selected: Boolean, color: Color) =
    if (selected) color else MaterialTheme.colorScheme.surface

private fun ResultDto.valueLabel(): String {
    val number = value?.let { "$it ${unit.orEmpty()}" } ?: "—"
    return spread?.let { "$number ± $it" } ?: number
}

private fun ResultDto.contextLabel(): String = listOfNotNull(
    if (origin == "cited") "цит. ${citedReference.orEmpty()}" else deviceId,
    aggregation,
    measurementId,
    page?.let { "стр. $it" },
    table ?: figure,
    duplicateOf?.let { "повтор $it" },
    fragment,
).joinToString(" · ")
