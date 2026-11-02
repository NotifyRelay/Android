package github.xzynine.superislandui.floating.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import notifyrelay.base.util.ColorUtils
import notifyrelay.base.util.DataUrlUtils
import notifyrelay.base.util.TextUtils

/**
 * 超级岛图片与文本工具类（平台无关部分）。
 *
 * 备选图标 URL 解析与简易 HTML 文本解析只使用公共 API；
 * 图片加载依赖平台解码能力，由 [rememberSuperIslandImagePainter] 的 actual 实现提供。
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
        val unescapedHtml = TextUtils.unescapeHtml(html)

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
}

/**
 * 加载超级岛图片为 [Painter]。
 *
 * data URL 用 Compose 的解码器直接解出位图；非 data URL 在本模块无法取网络字节，返回 null
 * （调用方按占位处理）。
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

    val bitmap by
        produceState<ImageBitmap?>(initialValue = null, key1 = resolvedUrl) {
            value =
                if (DataUrlUtils.isDataUrl(resolvedUrl)) {
                    DataUrlUtils.cleanDataUrl(resolvedUrl)?.let { decodeDataUrlPayload(it) }
                } else {
                    null
                }
        }
    return bitmap?.let { BitmapPainter(it) }
}

/** 解码 data URL 负载：base64 走标准解码，其余按 UTF-8 字节。 */
private fun decodeDataUrlPayload(cleaned: String): ImageBitmap? {
    val comma = cleaned.indexOf(',')
    if (comma <= 0) return null
    val meta = cleaned.substring(0, comma)
    val raw = cleaned.substring(comma + 1)
    val bytes =
        runCatching {
            if (meta.contains("base64", ignoreCase = true)) {
                kotlin.io.encoding.Base64
                    .decode(raw)
            } else {
                raw.toByteArray(Charsets.UTF_8)
            }
        }.getOrNull() ?: return null
    return runCatching { bytes.decodeToImageBitmap() }.getOrNull()
}
