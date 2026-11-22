package cybergarden.vinity.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Vinity",
        state = rememberWindowState(width = 1500.dp, height = 950.dp),
    ) {
        App(defaultApiUrl())
    }
}
