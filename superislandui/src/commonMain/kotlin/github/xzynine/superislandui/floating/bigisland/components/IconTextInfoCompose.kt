package github.xzynine.superislandui.floating.bigisland.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.superislandui.floating.common.rememberSuperIslandImagePainter
import github.xzynine.superislandui.floating.common.resolveIconUrl
import github.xzynine.superislandui.model.templates.IconTextInfo
import notifyrelay.base.util.ColorUtils
import notifyrelay.base.util.SuperIslandImageUtils
import top.yukonga.miuix.kmp.basic.Text

/**
 * 新图文组件（iconTextInfo）Compose 实现（OS3 模板 14 / 14-2 / 16 / 17 / 18 / 22）
 * 图片（48dp，圆角 12dp，显示区域 56dp）+ 主要文本 + 次要文本1 + 次要文本2（次要文本1/2 同一行）
 */
@Composable
fun IconTextInfoCompose(
    iconTextInfo: IconTextInfo,
    picMap: Map<String, String>? = null,
) {
    val preferDark = isSystemInDarkTheme()

    Row(
        modifier = Modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧图标：尺寸 48dp，圆角 12dp（图片尺寸 1/4），和文本整体纵向居中
        val iconKey =
            if (preferDark) {
                iconTextInfo.animIconInfo?.srcDark ?: iconTextInfo.animIconInfo?.src
            } else {
                iconTextInfo.animIconInfo?.src
            }

        var finalIconUrl: String? = resolveIconUrl(picMap, iconKey)
        if (finalIconUrl.isNullOrEmpty() && picMap != null) {
            finalIconUrl = SuperIslandImageUtils.resolveFallbackIconUrl(picMap)
        }

        if (!finalIconUrl.isNullOrEmpty()) {
            val painter = rememberSuperIslandImagePainter(finalIconUrl)
            painter?.let {
                Image(
                    painter = it,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp)),
                )
            }
        }

        val textColumnModifier =
            if (!finalIconUrl.isNullOrEmpty()) {
                Modifier.padding(start = 8.dp).weight(1f)
            } else {
                Modifier.weight(1f)
            }

        Column(modifier = textColumnModifier) {
            // 主要文本
            iconTextInfo.title?.let { text ->
                Text(
                    text = SuperIslandImageUtils.parseSimpleHtmlToAnnotatedString(text),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        Color(
                            (if (preferDark) ColorUtils.parseColor(iconTextInfo.colorTitleDark) else ColorUtils.parseColor(iconTextInfo.colorTitle))
                                ?: 0xFFFFFFFF.toInt(),
                        ),
                    maxLines = 1,
                )
            }

            // 次要文本1 + 次要文本2：同一行
            if (iconTextInfo.content != null || iconTextInfo.subContent != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val contentColor =
                        Color(
                            (if (preferDark) ColorUtils.parseColor(iconTextInfo.colorContentDark) else ColorUtils.parseColor(iconTextInfo.colorContent))
                                ?: 0xFFDDDDDD.toInt(),
                        )
                    ContentText(iconTextInfo.content, contentColor)
                    ContentText(iconTextInfo.subContent, contentColor, Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/**
 * 次要文本：解析简单 HTML，颜色按深浅色主题取正文色。
 *
 * @param text 原始文本；为空时不渲染。
 * @param preferDark 是否使用深色主题配色。
 * @param modifier 文本修饰符。
 */
@Composable
private fun ContentText(
    text: String?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    text?.let {
        Text(
            text = SuperIslandImageUtils.parseSimpleHtmlToAnnotatedString(it),
            fontSize = 12.sp,
            color = color,
            maxLines = 1,
            modifier = modifier,
        )
    }
}
