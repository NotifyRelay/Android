package github.xzynine.superislandui.floating.bigisland.components

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.superislandui.common.PreviewData
import github.xzynine.superislandui.floating.common.SuperIslandImageUtil
import github.xzynine.superislandui.floating.common.resolveIconUrl
import github.xzynine.superislandui.model.templates.IconTextInfo
import notifyrelay.core.util.image.ImageUtils

/**
 * 新图文组件（iconTextInfo）Compose 实现（OS3 模板 14 / 14-2 / 16 / 17 / 18 / 22）
 * 图片（48dp，圆角 12dp，显示区域 56dp）+ 主要文本 + 次要文本1 + 次要文本2（次要文本1/2 同一行）
 */
@Composable
fun IconTextInfoCompose(
    iconTextInfo: IconTextInfo,
    picMap: Map<String, String>? = null,
) {
    val uiMode = LocalConfiguration.current.uiMode
    val preferDark = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

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

        val context = LocalContext.current
        var finalIconUrl: String? = resolveIconUrl(picMap, iconKey, context)
        if (finalIconUrl.isNullOrEmpty() && picMap != null) {
            finalIconUrl = SuperIslandImageUtil.resolveFallbackIconUrl(picMap)
        }

        if (!finalIconUrl.isNullOrEmpty()) {
            val painter = SuperIslandImageUtil.rememberSuperIslandImagePainter(finalIconUrl)
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
                    text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(text),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        Color(
                            (if (preferDark) ImageUtils.parseColor(iconTextInfo.colorTitleDark) else ImageUtils.parseColor(iconTextInfo.colorTitle))
                                ?: 0xFFFFFFFF.toInt(),
                        ),
                    maxLines = 1,
                )
            }

            // 次要文本1 + 次要文本2：同一行
            if (iconTextInfo.content != null || iconTextInfo.subContent != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    iconTextInfo.content?.let { text ->
                        Text(
                            text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(text),
                            fontSize = 12.sp,
                            color =
                                Color(
                                    (if (preferDark) ImageUtils.parseColor(iconTextInfo.colorContentDark) else ImageUtils.parseColor(iconTextInfo.colorContent))
                                        ?: 0xFFDDDDDD.toInt(),
                                ),
                            maxLines = 1,
                        )
                    }
                    iconTextInfo.subContent?.let { text ->
                        Text(
                            text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(text),
                            fontSize = 12.sp,
                            color =
                                Color(
                                    (if (preferDark) ImageUtils.parseColor(iconTextInfo.colorContentDark) else ImageUtils.parseColor(iconTextInfo.colorContent))
                                        ?: 0xFFDDDDDD.toInt(),
                                ),
                            maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "新图文组件", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun IconTextInfoComposePreview() {
    IconTextInfoCompose(
        iconTextInfo = PreviewData.sampleIconTextInfo,
        picMap = PreviewData.samplePicMap,
    )
}
