package cybergarden.vinity.app

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentDisposition
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.awt.Cursor
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.util.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image

actual class LocalFile(val file: File) {
    actual val name: String get() = file.name
    actual val size: Long get() = file.length()
}

private var locationHash = ""

actual fun defaultApiUrl(): String = System.getProperty("vinity.api") ?: "http://localhost:8081"

actual fun ByteArray.toImageBitmap(): ImageBitmap = Image.makeFromEncoded(this).toComposeImageBitmap()

actual suspend fun pickFiles(extensions: List<String>, multiple: Boolean): List<LocalFile> = withContext(Dispatchers.IO) {
    val dialog = FileDialog(null as Frame?, "Выберите файлы", FileDialog.LOAD).apply {
        isMultipleMode = multiple
        setFilenameFilter { _, name -> extensions.any { name.endsWith(".$it", ignoreCase = true) } }
    }
    dialog.isVisible = true
    dialog.files.map(::LocalFile)
}

actual fun listenForDroppedFiles(onDrag: (Boolean) -> Unit, onFiles: (List<LocalFile>) -> Unit) = Unit

actual suspend fun postFiles(url: String, part: String, files: List<LocalFile>, onProgress: (Long, Long) -> Unit): String {
    val contents = withContext(Dispatchers.IO) { files.map { it.name to it.file.readBytes() } }
    return HttpClient(CIO).use { http ->
        val response = http.submitFormWithBinaryData(
            url = url,
            formData = formData {
                contents.forEach { (name, bytes) -> append(part, bytes, fileHeaders(name)) }
            },
        ) {
            onUpload { sent, total -> onProgress(sent, total ?: sent) }
        }
        check(response.status.isSuccess()) { "сервер ответил ${response.status}" }
        response.bodyAsText()
    }
}

actual fun download(url: String) {
    Desktop.getDesktop().browse(URI(url))
}

actual fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

actual fun readLocationHash(): String = locationHash

actual fun writeLocationHash(hash: String) {
    locationHash = hash
}

actual fun listenForLocationHash(onChange: (String) -> Unit) = Unit

actual fun shareableLink(hash: String): String = "${defaultApiUrl()}/#$hash"

private fun fileHeaders(name: String) = Headers.build {
    append(HttpHeaders.ContentDisposition, ContentDisposition.File.withParameter(ContentDisposition.Parameters.FileName, name).toString())
}

private val settings: Preferences = Preferences.userRoot().node("cybergarden/vinity")

actual fun loadSetting(key: String): String? = settings.get(key, null)

actual fun saveSetting(key: String, value: String?) {
    if (value == null) settings.remove(key) else settings.put(key, value)
}

actual fun Modifier.resizeCursor(): Modifier = pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)), overrideDescendants = true)
