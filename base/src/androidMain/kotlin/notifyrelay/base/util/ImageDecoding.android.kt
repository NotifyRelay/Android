package notifyrelay.base.util

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Android 实现：经 [BitmapFactory] 解码。 */
actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    if (bytes.isEmpty()) return null
    return runCatching {
        BitmapFactory
            .decodeByteArray(bytes, 0, bytes.size)
            ?.asImageBitmap()
    }.getOrNull()
}
