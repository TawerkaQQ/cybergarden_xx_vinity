package cybergarden.vinity.app

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.browser.window
import org.jetbrains.skia.Image

private const val DEV_SERVER_PORT = "8080"
private const val API_PORT = "8081"

actual fun defaultApiUrl(): String {
    val location = window.location
    return if (location.port == DEV_SERVER_PORT) "${location.protocol}//${location.hostname}:$API_PORT" else location.origin
}

actual fun ByteArray.toImageBitmap(): ImageBitmap = Image.makeFromEncoded(this).toComposeImageBitmap()
