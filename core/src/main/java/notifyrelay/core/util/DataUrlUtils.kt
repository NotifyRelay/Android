package notifyrelay.core.util

import android.content.Context
import android.graphics.Bitmap
import notifyrelay.core.util.image.ImageUtils

/**
 * 已迁移至 [ImageUtils]。
 * 此类中的所有方法均已标记为废弃，请直接使用 [ImageUtils] 替代。
 */
@Deprecated("已迁移至 ImageUtils，请使用 notifyrelay.core.util.image.ImageUtils")
object DataUrlUtils {
    private const val TAG = "DataUrlUtils"
    private const val DATA_PREFIX = "data:"

    @Deprecated("迁移至 ImageUtils", ReplaceWith("ImageUtils.findDataUrls(text)"))
    fun findDataUrls(text: String): List<String> = ImageUtils.findDataUrls(text)

    @Deprecated("迁移至 ImageUtils", ReplaceWith("ImageUtils.splitByDataUrls(text)"))
    fun splitByDataUrls(text: String): List<Pair<String?, String?>> = ImageUtils.splitByDataUrls(text)

    @Deprecated("迁移至 ImageUtils", ReplaceWith("ImageUtils.isDataUrl(text)"))
    fun isDataUrl(text: String): Boolean = ImageUtils.isDataUrl(text)

    @Deprecated("迁移至 ImageUtils", ReplaceWith("ImageUtils.decodeDataUrlToBitmap(context, dataUrl)"))
    suspend fun decodeDataUrlToBitmap(
        context: Context,
        dataUrl: String,
    ): Bitmap? = ImageUtils.decodeDataUrlToBitmap(context, dataUrl)

    @Deprecated("迁移至 ImageUtils", ReplaceWith("ImageUtils.loadBitmap(context, uri)"))
    suspend fun loadBitmapWithCoil(
        context: Context,
        uri: Any,
    ): Bitmap? = ImageUtils.loadBitmap(context, uri)

    @Deprecated("迁移至 ImageUtils", ReplaceWith("ImageUtils.bitmapToDataUri(bitmap)"))
    fun bitmapToDataUri(bitmap: Bitmap): String = ImageUtils.bitmapToDataUri(bitmap)
}
