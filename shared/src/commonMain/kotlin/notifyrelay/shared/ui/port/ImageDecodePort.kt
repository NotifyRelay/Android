package notifyrelay.shared.ui.port

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/** 图片解码与缓存端口。 */
interface ImageDecodePort {
    /** 取缓存。 */
    fun cached(data: String): ImageBitmap?

    /** 写入缓存。 */
    fun putCache(
        data: String,
        bitmap: ImageBitmap,
    )

    /** 解码图片数据；失败返回 null。 */
    suspend fun decode(data: String): ImageBitmap?
}

/** 由宿主提供实现。 */
val LocalImageDecodePort = staticCompositionLocalOf<ImageDecodePort?> { null }
