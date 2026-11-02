package notifyrelay.base.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

/**
 * 超级岛图片与文本工具（纯函数）。
 *
 * 备选图标 URL 解析与简易 HTML 文本解析只使用公共 API，
 * 故置于 `:base` 供 `:shared` 与 `:superislandui` 共用；图片加载依赖平台解码能力，不在本类。
 */
object SuperIslandImageUtils {
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
