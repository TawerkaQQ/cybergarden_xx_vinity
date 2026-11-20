package cybergarden.vinity.viewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cybergarden.vinity.data.csv.CsvSubmissionRepository
import cybergarden.vinity.viewer.pdf.PdfPageRenderer
import java.nio.file.Path

fun main() = application {
    val root = Path.of(System.getProperty("vinity.root") ?: "..").toAbsolutePath().normalize()
    val state = remember { ViewerState(root, CsvSubmissionRepository(), PdfPageRenderer()) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Vinity",
        state = rememberWindowState(width = 1500.dp, height = 950.dp),
    ) {
        MaterialTheme {
            ViewerScreen(state)
        }
    }
}
