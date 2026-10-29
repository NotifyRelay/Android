package notifyrelay.base.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap

/** JVM 实现：经 Compose 的 Skia 解码器解码。 */
actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    if (bytes.isEmpty()) return null
    return runCatching { bytes.decodeToImageBitmap() }.getOrNull()
}
