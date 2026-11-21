package cybergarden.vinity.app

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Image
import org.w3c.dom.DragEvent
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileList
import org.w3c.files.get
import org.w3c.xhr.FormData
import org.w3c.xhr.ProgressEvent
import org.w3c.xhr.XMLHttpRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val DEV_SERVER_PORT = "8080"
private const val API_PORT = "8081"

actual class LocalFile(val file: File) {
    actual val name: String get() = file.name
    actual val size: Long get() = file.size.toDouble().toLong()
}

actual fun defaultApiUrl(): String {
    val location = window.location
    return if (location.port == DEV_SERVER_PORT) "${location.protocol}//${location.hostname}:$API_PORT" else location.origin
}

actual fun ByteArray.toImageBitmap(): ImageBitmap = Image.makeFromEncoded(this).toComposeImageBitmap()

actual suspend fun pickFiles(extensions: List<String>, multiple: Boolean): List<LocalFile> = suspendCancellableCoroutine { continuation ->
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = extensions.joinToString(",") { ".$it" }
    input.multiple = multiple
    input.addEventListener("change") {
        if (continuation.isActive) continuation.resume(input.files.toLocalFiles())
    }
    input.addEventListener("cancel") {
        if (continuation.isActive) continuation.resume(emptyList())
    }
    input.click()
}

actual fun listenForDroppedFiles(onDrag: (Boolean) -> Unit, onFiles: (List<LocalFile>) -> Unit) {
    document.addEventListener("dragover") { event ->
        event.preventDefault()
        onDrag(true)
    }
    document.addEventListener("dragleave") { event ->
        if (event.unsafeCast<DragEvent>().relatedTarget == null) onDrag(false)
    }
    document.addEventListener("drop") { event ->
        event.preventDefault()
        onDrag(false)
        val files = event.unsafeCast<DragEvent>().dataTransfer?.files.toLocalFiles()
        if (files.isNotEmpty()) onFiles(files)
    }
}

actual suspend fun postFiles(url: String, part: String, files: List<LocalFile>, onProgress: (Long, Long) -> Unit): String =
    suspendCancellableCoroutine { continuation ->
        val form = FormData()
        files.forEach { form.append(part, it.file, it.file.name) }
        val request = XMLHttpRequest()
        request.open("POST", url)
        request.upload.onprogress = { event: ProgressEvent ->
            onProgress(event.loaded.toDouble().toLong(), event.total.toDouble().toLong())
            null
        }
        request.onload = {
            val status = request.status.toInt()
            if (status in 200..299) {
                continuation.resume(request.responseText)
            } else {
                continuation.resumeWithException(IllegalStateException("сервер ответил $status"))
            }
            null
        }
        request.onerror = {
            continuation.resumeWithException(IllegalStateException("нет связи с сервером"))
            null
        }
        continuation.invokeOnCancellation { request.abort() }
        request.send(form)
    }

actual fun download(url: String) {
    val anchor = document.createElement("a") as HTMLAnchorElement
    anchor.href = url
    anchor.download = ""
    anchor.click()
}

actual fun copyToClipboard(text: String) {
    window.navigator.clipboard.writeText(text)
}

actual fun readLocationHash(): String = window.location.hash.removePrefix("#")

actual fun writeLocationHash(hash: String) {
    if (readLocationHash() != hash) window.location.hash = hash
}

actual fun listenForLocationHash(onChange: (String) -> Unit) {
    window.addEventListener("hashchange") { onChange(readLocationHash()) }
}

actual fun shareableLink(hash: String): String = window.location.href.substringBefore('#') + "#" + hash

private fun FileList?.toLocalFiles(): List<LocalFile> {
    if (this == null) return emptyList()
    return (0 until length).mapNotNull { index -> get(index)?.let(::LocalFile) }
}
