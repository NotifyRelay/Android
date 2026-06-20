package github.xzynine.superislandui.floating.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import coil.compose.rememberAsyncImagePainter
import notifyrelay.core.util.image.ImageUtils

/**
 * 超级岛图片加载和处理工具类
 * 整合了图片加载、Compose 图片加载和辅助工具函数
 */
object SuperIslandImageUtil {

    /**
     * 统一的Compose图片加载工具，封装了现有ImageLoader和DataUrlUtils的功能
     */
    @Composable
    fun rememberSuperIslandImagePainter(
        url: String?, 
        picMap: Map<String, String>? = null,
        iconKey: String? = null
    ): Painter? {
        // 预览模式下返回null，由调用方处理占位符
        if (LocalInspectionMode.current) return null

        val resolvedUrl = remember(url, picMap, iconKey) {
            if (!iconKey.isNullOrEmpty() && picMap != null) {
                picMap[iconKey]
            } else {
                url
            }
        }

        if (resolvedUrl.isNullOrEmpty()) return null

        // data URL 使用 ImageUtils.loadBitmap（走 decodeDataUrlToBitmap），与通知路径一致
        if (ImageUtils.isDataUrl(resolvedUrl)) {
            val context = LocalContext.current
            val bitmap by produceState<Bitmap?>(initialValue = null, key1 = resolvedUrl) {
                value = ImageUtils.loadBitmap(context, resolvedUrl)
            }
            return bitmap?.let { BitmapPainter(it.asImageBitmap()) }
        }

        // 非 data URL 仍然使用 Coil
        return rememberAsyncImagePainter(model = resolvedUrl)
    }

    /**
     * 解析备选图标URL，与View渲染的逻辑完全一致
     */
    fun resolveFallbackIconUrl(picMap: Map<String, String>?): String? {
        if (picMap == null) return null

        val prefix = "miui.focus.pic_"
        val focusKeys = picMap.keys
            .filter { it.startsWith(prefix) }
            .toList()

        val secondKey = focusKeys.getOrNull(1)
        if (secondKey != null) {
            var url = picMap[secondKey]
            if (!url.isNullOrEmpty()) {
                // 原 resolveReferenceUrl 为空壳方法直接返回输入，此处直接使用 url
            }
            return url
        }

        return null
    }

    /**
     * 解析简单HTML标签，将其转换为AnnotatedString
     */
    fun parseSimpleHtmlToAnnotatedString(html: String): androidx.compose.ui.text.AnnotatedString {
        val builder = androidx.compose.ui.text.AnnotatedString.Builder()
        val unescapedHtml = ImageUtils.unescapeHtml(html)
        
        // 简单的HTML解析，只处理font标签的color属性
        var index = 0
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
                if (endTagName.equals("font", ignoreCase = true)) {
                    builder.pop()
                }
                index = tagEnd + 1
            } else {
                if (tag.startsWith("font", ignoreCase = true)) {
                    val colorMatch = Regex("color=['\"](#[0-9A-Fa-f]{6}|#[0-9A-Fa-f]{3})['\"]").find(tag)
                    colorMatch?.let { matchResult ->
                        val colorValue = matchResult.groupValues[1]
                        val colorInt = ImageUtils.parseColor(colorValue) ?: 0xFFFFFFFF.toInt()
                        builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color(colorInt)))
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