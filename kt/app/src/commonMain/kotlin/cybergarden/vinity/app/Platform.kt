package cybergarden.vinity.app

import androidx.compose.ui.graphics.ImageBitmap

expect fun defaultApiUrl(): String

expect fun ByteArray.toImageBitmap(): ImageBitmap
