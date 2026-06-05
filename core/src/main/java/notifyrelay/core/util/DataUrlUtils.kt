package notifyrelay.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import coil.Coil
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import notifyrelay.base.util.Logger
import java.io.ByteArrayOutputStream

object DataUrlUtils {
    private const val TAG = "DataUrlUtils"
    private const val DATA_PREFIX = "data:"

    fun findDataUrls(text: String): List<String> {
        val results = mutableListOf<String>()
        var start = text.indexOf(DATA_PREFIX, 0, ignoreCase = true)
        while (start >= 0) {
            val end = findDataEndCandidate(text, start)
            results.add(text.substring(start, end))
            start = text.indexOf(DATA_PREFIX, end, ignoreCase = true)
        }
        return results
    }

    fun splitByDataUrls(text: String): List<Pair<String?, String?>> {
        val parts = mutableListOf<Pair<String?, String?>>()
        var lastIndex = 0
        var start = text.indexOf(DATA_PREFIX, 0, ignoreCase = true)
        while (start >= 0) {
            if (start > lastIndex) parts.add(Pair(text.substring(lastIndex, start), null))
            val end = findDataEndCandidate(text, start)
            parts.add(Pair(null, text.substring(start, end)))
            lastIndex = end
            start = text.indexOf(DATA_PREFIX, lastIndex, ignoreCase = true)
        }
        if (lastIndex < text.length) parts.add(Pair(text.substring(lastIndex), null))
        return parts
    }

    private fun findDataEndCandidate(text: String, startIndex: Int): Int {
        var i = startIndex
        val len = text.length
        while (i < len) {
            val c = text[i]
            if (c == '"' || c == '\'') {
                if (i > startIndex) return i
            }
            if ((c == '}' || c == ']' || c == ',' || c.isWhitespace()) && i > startIndex) {
                val commaAfter = text.indexOf(',', startIndex)
                if (commaAfter in (startIndex + 1) until i) return i
            }
            i++
        }
        return len
    }

    suspend fun decodeDataUrlToBitmap(context: Context, dataUrl: String): Bitmap? {
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
                Logger.e(TAG, "解码 data URL 失败: ${e.message}", e)
                null
            }
        }
    }

    suspend fun loadBitmapWithCoil(context: Context, uri: Any): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val loader = Coil.imageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    if (drawable is BitmapDrawable) return@withContext drawable.bitmap
                    drawableToBitmap(drawable)
                } else {
                    val error = (result as? ErrorResult)?.throwable
                    if (error != null) Logger.e(TAG, "loadBitmapWithCoil 失败: ${error.message}", error)
                    else Logger.e(TAG, "loadBitmapWithCoil 失败: 未知错误")
                    null
                }
            } catch (e: Exception) {
                Logger.e(TAG, "loadBitmapWithCoil 异常: ${e.message}", e)
                null
            }
        }
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) return drawable.bitmap
        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }

    fun bitmapToDataUri(bitmap: Bitmap): String {
        return try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val b = baos.toByteArray()
            val b64 = Base64.encodeToString(b, Base64.NO_WRAP)
            "data:image/png;base64,$b64"
        } catch (e: Exception) {
            ""
        }
    }

    fun isDataUrl(text: String): Boolean = text.trim().startsWith(DATA_PREFIX, ignoreCase = true)

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
