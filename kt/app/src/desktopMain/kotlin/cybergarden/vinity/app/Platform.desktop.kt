package cybergarden.vinity.app

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual fun defaultApiUrl(): String = System.getProperty("vinity.api") ?: "http://localhost:8081"

actual fun ByteArray.toImageBitmap(): ImageBitmap = Image.makeFromEncoded(this).toComposeImageBitmap()
