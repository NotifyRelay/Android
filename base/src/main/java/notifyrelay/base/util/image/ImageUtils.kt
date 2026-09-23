package notifyrelay.base.util.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import coil.Coil
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import notifyrelay.base.util.Logger
import java.io.ByteArrayOutputStream

/**
 * 图片处理工具类
 *
 * 整合了以下来源的工具方法：
 * - data URL 解码、编解码与 Base64 编码
 * - SuperIslandImageUtil：颜色解析、HTML 转义处理
 * - Coil 图片加载方法的统一封装
 *
 * 顶层另有 [notifyrelay.base.util.image.toBitmapOrDefault]（drawable→Bitmap 样板归一）。
 */
object ImageUtils {
    private const val TAG = "ImageUtils"
    private const val DATA_PREFIX = "data:"

    // ==================== Data URL 相关 ====================

    fun isDataUrl(text: String): Boolean = text.trim().startsWith(DATA_PREFIX, ignoreCase = true)

    suspend fun decodeDataUrlToBitmap(
        context: Context,
        dataUrl: String,
    ): Bitmap? {
        val cleaned = cleanDataUrl(dataUrl)
        if (cleaned == null) {
            Logger.w(TAG, "data URL 格式无效，原始前64字符: ${dataUrl.take(64)}")
            return null
        }
        return withContext(Dispatchers.IO) {
            try {
                val comma = cleaned.indexOf(',')
                val meta = cleaned.substring(5, comma)
                val rawData = cleaned.substring(comma + 1)
                if (meta.contains("base64", ignoreCase = true)) {
                    val bytes = Base64.decode(rawData, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    val decoded = java.net.URLDecoder.decode(rawData, "UTF-8")
                    val bytes = decoded.toByteArray(Charsets.UTF_8)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "解码 data URL 失败: ", e)
                null
            }
        }
    }

    /**
     * 把位图编码为**不带** data URI 前缀的 base64 字符串（PNG / quality 100 / [Base64.NO_WRAP]）。
     *
     * 抛异常语义：本方法**不吞异常**（与 `IconSyncManager` 的私有实现一致），
     * 编码失败时异常向上抛出；需要「失败返回空串」的宽容语义请使用 [bitmapToDataUri]。
     *
     * @param bitmap 待编码的位图。
     * @return 不含 `data:image/png;base64,` 前缀的 base64 文本。
     */
    fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * 把位图编码为 `data:image/png;base64,<b64>` 形式的 data URI。
     *
     * 异常语义保持不变：内部捕获异常并返回空串 `""`（原有行为）。
     *
     * @param bitmap 待编码的位图。
     * @return 带 `data:image/png;base64,` 前缀的 data URI；编码失败时返回 `""`。
     */
    fun bitmapToDataUri(bitmap: Bitmap): String =
        try {
            "data:image/png;base64,${bitmapToBase64(bitmap)}"
        } catch (e: Exception) {
            ""
        }

    /**
     * 把字节数组拼成 `data:<mime>;base64,<b64>` 形式的 data URI。
     *
     * @param bytes 原始字节数组。
     * @param mime data URI 的 MIME 类型，例如 `image/png`、`image/jpeg`。
     * @return 带前缀的 data URI 字符串。
     */
    fun bytesToDataUrl(
        bytes: ByteArray,
        mime: String,
    ): String = "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"

    // ==================== 统一图片加载 ====================

    suspend fun loadBitmap(
        context: Context,
        uri: Any,
    ): Bitmap? {
        if (uri is String && uri.trim().startsWith(DATA_PREFIX, ignoreCase = true)) {
            return decodeDataUrlToBitmap(context, uri)
        }
        return withContext(Dispatchers.IO) {
            try {
                val loader = Coil.imageLoader(context)
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(uri)
                        .allowHardware(false)
                        .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    if (drawable is BitmapDrawable) return@withContext drawable.bitmap
                    drawable.toBitmap()
                } else {
                    val error = (result as? ErrorResult)?.throwable
                    if (error != null) {
                        Logger.e(TAG, "loadBitmap 失败: ", error)
                    } else {
                        Logger.e(TAG, "loadBitmap 失败: 未知错误")
                    }
                    null
                }
            } catch (e: Exception) {
                Logger.e(TAG, "loadBitmap 异常: ", e)
                null
            }
        }
    }

    // ==================== 颜色与文本工具 ====================

    fun parseColor(colorString: String?): Int? =
        try {
            colorString?.toColorInt()
        } catch (e: Exception) {
            null
        }

    fun unescapeHtml(input: String): String =
        input
            .replace("\\u003c", "<")
            .replace("\\u003e", ">")
            .replace("\\u0027", "'")
            .replace("\\u0022", "\"")
            .replace("\\u0026", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    // ==================== 位图转换 ====================

    /**
     * 等比缩小位图，使最长边不超过 [maxDimension]；已足够小或尺寸非法时原样返回。
     *
     * @param bitmap 原始位图。
     * @param maxDimension 允许的最长边像素数。
     * @return 缩放后的位图；无需缩放时返回 [bitmap] 自身。
     */
    fun scaleDown(
        bitmap: Bitmap,
        maxDimension: Int,
    ): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension || longest <= 0) return bitmap
        val ratio = maxDimension.toFloat() / longest
        val targetWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    // ==================== 私有辅助方法 ====================

    private fun cleanDataUrl(dataUrl: String): String? {
        var candidate = dataUrl.trim()
        if (candidate.length >= 2 && candidate.first() == '"' && candidate.last() == '"') {
            candidate = candidate.substring(1, candidate.length - 1)
        }
        candidate = candidate.replace("\\/", "/")
        candidate = candidate.replace("\\\\", "")
        if (!candidate.startsWith(DATA_PREFIX, ignoreCase = true)) {
            Logger.w(TAG, "cleanDataUrl: 不以 data: 开头，原始前64字符: ${dataUrl.take(64)}")
            return null
        }
        val comma = candidate.indexOf(',')
        if (comma <= 0) {
            Logger.w(TAG, "cleanDataUrl: 未找到逗号分隔符，清理后前64字符: ${candidate.take(64)}")
            return null
        }
        return candidate
    }
}

/**
 * 把 [Drawable] 转成 [Bitmap]；非 [BitmapDrawable] 时按 intrinsic 尺寸创建，空尺寸用 [fallbackSize] 兜底。
 *
 * 对 `null` 的 Drawable 不做兜底 —— 可空语义由调用侧用 `?.` 自行处理
 * （例如 `drawable?.toBitmapOrDefault(48) ?: <原兜底>`）。
 *
 * 以**顶层扩展**形式提供（与 `notifyrelay.base.util.toHex` 风格一致），
 * 使调用侧可直接写 `drawable.toBitmapOrDefault(96)` 而不必经由 `ImageUtils` 接收者作用域。
 *
 * @param fallbackSize intrinsic 宽或高非正数时使用的兜底边长（默认 96；默认图标场景可用 48，极小占位可用 1）。
 * @return 转换得到的位图。
 */
fun Drawable.toBitmapOrDefault(fallbackSize: Int = 96): Bitmap {
    if (this is BitmapDrawable) return bitmap
    val width = intrinsicWidth.takeIf { it > 0 } ?: fallbackSize
    val height = intrinsicHeight.takeIf { it > 0 } ?: fallbackSize
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, width, height)
    draw(canvas)
    return bmp
}
