package github.xzynine.superislandui.floating.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import notifyrelay.shared.util.ColorUtils
import java.net.URLDecoder

/** data URL 前缀。 */
private const val DATA_PREFIX = "data:"

/**
 * 超级岛图片与文本工具类。
 *
 * 图片加载在 Android 侧实现：data URL 解码为位图，其余 URL 交由 Coil 加载。
 */
object SuperIslandImageUtil {
    /** 备选图标键前缀，取自超级岛协议中 focus 图标的键名约定。 */
    private const val FOCUS_PIC_PREFIX = "miui.focus.pic_"

    /** font 标签的 color 属性，支持 `#RRGGBB` 与 `#RGB` 两种写法。 */
    private val FONT_COLOR_PATTERN = Regex("color=['\"](#[0-9A-Fa-f]{6}|#[0-9A-Fa-f]{3})['\"]")

    /** color 属性解析失败时使用的默认颜色。 */
    private const val DEFAULT_TEXT_COLOR = -1

    /**
     * 解析备选图标 URL：取 [picMap] 中前缀为 [FOCUS_PIC_PREFIX] 的第二个键对应的值。
     *
     * @param picMap 图标键值表，可为 null。
     * @return 备选图标 URL；无第二个可选键时返回 null。
     */
    fun resolveFallbackIconUrl(picMap: Map<String, String>?): String? {
        if (picMap == null) return null
        val secondKey =
            picMap.keys
                .filter { it.startsWith(FOCUS_PIC_PREFIX) }
                .toList()
                .getOrNull(1)
                ?: return null
        return picMap[secondKey]
    }

    /**
     * 解析简单 HTML 标签，转换为 [AnnotatedString]。
     *
     * 仅处理 font 标签的 color 属性，其余标签按文本原文处理。
     *
     * @param html 原始带标签文本。
     * @return 带样式文本。
     */
    fun parseSimpleHtmlToAnnotatedString(html: String): AnnotatedString {
        val builder = AnnotatedString.Builder()
        val unescapedHtml = unescapeHtml(html)

        var index = 0
        var fontStyleDepth = 0
        while (index < unescapedHtml.length) {
            val tagStart = unescapedHtml.indexOf('<', index)
            if (tagStart == -1) {
                builder.append(unescapedHtml.substring(index))
                break
            }

            if (tagStart > index) {
                builder.append(unescapedHtml.substring(index, tagStart))
            }

            val tagEnd = unescapedHtml.indexOf('>', tagStart)
            if (tagEnd == -1) {
                builder.append(unescapedHtml.substring(tagStart))
                break
            }

            val tag = unescapedHtml.substring(tagStart + 1, tagEnd)

            if (tag.startsWith("/")) {
                val endTagName = tag.substring(1).trim()
                if (endTagName.equals("font", ignoreCase = true) && fontStyleDepth > 0) {
                    builder.pop()
                    fontStyleDepth--
                }
                index = tagEnd + 1
            } else {
                if (tag.startsWith("font", ignoreCase = true)) {
                    val colorMatch = FONT_COLOR_PATTERN.find(tag)
                    if (colorMatch != null) {
                        val colorInt = ColorUtils.parseColor(colorMatch.groupValues[1]) ?: DEFAULT_TEXT_COLOR
                        builder.pushStyle(SpanStyle(color = Color(colorInt)))
                        fontStyleDepth++
                    }
                }

                index = tagEnd + 1

                val nextTagStart = unescapedHtml.indexOf('<', index)
                if (nextTagStart != -1) {
                    builder.append(unescapedHtml.substring(index, nextTagStart))
                    index = nextTagStart
                } else {
                    builder.append(unescapedHtml.substring(index))
                    break
                }
            }
        }

        return builder.toAnnotatedString()
    }

    /**
     * 还原文本中的转义序列与 HTML 实体。
     */
    private fun unescapeHtml(input: String): String =
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
}

/**
 * 加载超级岛图片为 [Painter]。
 *
 * @param url 图片 URL；可为 data URL。
 * @param picMap 图标键值表，非空时按 [iconKey] 取值。
 * @param iconKey 图标键，非空时优先于 [url]。
 * @return 图片 [Painter]；无可用 URL 或预览模式下返回 null。
 */
@Composable
fun rememberSuperIslandImagePainter(
    url: String?,
    picMap: Map<String, String>? = null,
    iconKey: String? = null,
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
