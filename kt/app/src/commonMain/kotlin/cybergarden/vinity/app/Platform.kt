package cybergarden.vinity.app

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

expect class LocalFile {
    val name: String
    val size: Long
}

expect fun defaultApiUrl(): String

expect fun ByteArray.toImageBitmap(): ImageBitmap

expect suspend fun pickFiles(extensions: List<String>, multiple: Boolean): List<LocalFile>

expect fun listenForDroppedFiles(onDrag: (Boolean) -> Unit, onFiles: (List<LocalFile>) -> Unit)

expect suspend fun postFiles(url: String, part: String, files: List<LocalFile>, onProgress: (Long, Long) -> Unit): String

expect fun download(url: String)

expect fun copyToClipboard(text: String)

expect fun readLocationHash(): String

expect fun writeLocationHash(hash: String)

expect fun listenForLocationHash(onChange: (String) -> Unit)

expect fun shareableLink(hash: String): String

expect fun loadSetting(key: String): String?

expect fun saveSetting(key: String, value: String?)

expect fun Modifier.resizeCursor(): Modifier

expect fun openUrl(url: String)
