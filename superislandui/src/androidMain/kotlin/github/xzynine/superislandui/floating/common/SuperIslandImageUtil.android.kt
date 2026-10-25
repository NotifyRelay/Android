package github.xzynine.superislandui.floating.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalInspectionMode
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder

/** data URL 前缀。 */
private const val DATA_PREFIX = "data:"

// Android 侧图片加载实现：data URL 直接解码为位图，其余 URL 交由 Coil 加载。
@Composable
actual fun rememberSuperIslandImagePainter(
    url: String?,
    picMap: Map<String, String>?,
    iconKey: String?,
): Painter? {
    // 预览模式下返回 null，由调用方处理占位符
    if (LocalInspectionMode.current) return null

    val resolvedUrl =
        remember(url, picMap, iconKey) {
            if (!iconKey.isNullOrEmpty() && picMap != null) {
                picMap[iconKey]
            } else {
                url
            }
        }

    if (resolvedUrl.isNullOrEmpty()) return null

    // data URL 直接解码为位图，与通知路径的取图口径一致
    if (resolvedUrl.trim().startsWith(DATA_PREFIX, ignoreCase = true)) {
        val bitmap by produceState<Bitmap?>(initialValue = null, key1 = resolvedUrl) {
            value = decodeDataUrlToBitmap(resolvedUrl)
        }
        return bitmap?.let { BitmapPainter(it.asImageBitmap()) }
    }

    // 非 data URL 仍然使用 Coil
    return rememberAsyncImagePainter(model = resolvedUrl)
}

/**
 * 把 data URL 解码为位图；格式无效或解码失败时返回 null。
 */
private suspend fun decodeDataUrlToBitmap(dataUrl: String): Bitmap? =
    withContext(Dispatchers.IO) {
        val cleaned = cleanDataUrl(dataUrl) ?: return@withContext null
        try {
            val comma = cleaned.indexOf(',')
            val meta = cleaned.substring(DATA_PREFIX.length, comma)
            val rawData = cleaned.substring(comma + 1)
            val bytes =
                if (meta.contains("base64", ignoreCase = true)) {
                    Base64.decode(rawData, Base64.DEFAULT)
                } else {
                    URLDecoder.decode(rawData, "UTF-8").toByteArray(Charsets.UTF_8)
                }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

/**
 * 规范化 data URL 文本：去首尾空白与包裹引号、还原转义斜杠，并校验前缀与逗号分隔符。
 */
private fun cleanDataUrl(dataUrl: String): String? {
    var candidate = dataUrl.trim()
    if (candidate.length >= 2 && candidate.first() == '"' && candidate.last() == '"') {
        candidate = candidate.substring(1, candidate.length - 1)
    }
    candidate = candidate.replace("\\/", "/")
    candidate = candidate.replace("\\\\", "")
    if (!candidate.startsWith(DATA_PREFIX, ignoreCase = true)) return null
    val comma = candidate.indexOf(',')
    if (comma <= 0) return null
    return candidate
}
