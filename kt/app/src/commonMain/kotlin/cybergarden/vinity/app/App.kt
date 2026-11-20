package cybergarden.vinity.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cybergarden.vinity.app.ui.BrowserScreen
import cybergarden.vinity.app.ui.QualityScreen
import cybergarden.vinity.app.ui.VinityTheme

private enum class Screen(val title: String) { BROWSER("Статьи"), QUALITY("Качество") }

@Composable
fun App(apiUrl: String) {
    val scope = rememberCoroutineScope()
    val store = remember { ViewerStore(VinityClient(apiUrl), scope) }
    var screen by remember { mutableStateOf(Screen.BROWSER) }
    LaunchedEffect(Unit) { store.start() }

    VinityTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopBar(store, screen) { screen = it }
                store.error?.let {
                    Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                }
                HorizontalDivider()
                when (screen) {
                    Screen.BROWSER -> BrowserScreen(store)
                    Screen.QUALITY -> QualityScreen(store)
                }
            }
        }
    }
}

@Composable
private fun TopBar(store: ViewerStore, screen: Screen, onScreen: (Screen) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Vinity", style = MaterialTheme.typography.titleLarge)
        store.datasets.forEach { name ->
            FilterChip(selected = store.dataset?.name == name, onClick = { store.open(name) }, label = { Text(name) })
        }
        store.dataset?.let {
            Text(
                "статей ${it.publications.size} · приборов ${it.deviceCount} · значений ${it.results.size} · замечаний ${it.issues.size}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TabRow(selectedTabIndex = screen.ordinal, modifier = Modifier.width(260.dp)) {
            Screen.entries.forEach { entry ->
                Tab(selected = screen == entry, onClick = { onScreen(entry) }, text = { Text(entry.title) })
            }
        }
    }
}
