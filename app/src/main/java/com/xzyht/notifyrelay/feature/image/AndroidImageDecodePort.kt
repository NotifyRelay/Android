package com.xzyht.notifyrelay.feature.image

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.xzyht.notifyrelay.ui.pages.superisland.SuperIslandImageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import notifyrelay.shared.ui.port.ImageDecodePort

/** 以 [SuperIslandImageStore] 与 [SuperIslandImageCache] 实现 [ImageDecodePort]。 */
class AndroidImageDecodePort(
    private val context: Context,
) : ImageDecodePort {
    override fun cached(data: String): ImageBitmap? = SuperIslandImageCache.get(data)?.asImageBitmap()

    override fun putCache(
        data: String,
        bitmap: ImageBitmap,
    ) {
        SuperIslandImageCache.put(data, bitmap.asAndroidBitmap())
    }

    override suspend fun decode(data: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
                .decodeBitmap(context, data)
                ?.asImageBitmap()
        }
}
