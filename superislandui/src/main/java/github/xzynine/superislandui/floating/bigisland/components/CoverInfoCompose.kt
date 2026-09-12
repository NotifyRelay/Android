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
import github.xzynine.superislandui.model.templates.CoverInfo
import notifyrelay.core.util.image.ImageUtils

/**
 * 封面组件（coverInfo）Compose 实现（OS3 模板 19）
 * 封面图（picCover，与文本整体纵向居中）+ 主要文本（title）+ 次要文本1（content）+ 次要文本2（subContent）
 * 封面图建议像素不小于 224*288（3:4），此处按 48dp x 64dp 渲染
 */
@Composable
fun CoverInfoCompose(
    coverInfo: CoverInfo,
    picMap: Map<String, String>? = null,
) {
    val uiMode = LocalConfiguration.current.uiMode
    val preferDark = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    Row(
        modifier = Modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 封面图：与文本整体纵向居中
        val coverKey = coverInfo.picCover
        val context = LocalContext.current
        var finalCoverUrl: String? = resolveIconUrl(picMap, coverKey, context)
        if (finalCoverUrl.isNullOrEmpty() && picMap != null) {
            finalCoverUrl = SuperIslandImageUtil.resolveFallbackIconUrl(picMap)
        }

        if (!finalCoverUrl.isNullOrEmpty()) {
            val painter = SuperIslandImageUtil.rememberSuperIslandImagePainter(finalCoverUrl)
            painter?.let {
                Image(
                    painter = it,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .padding(end = 8.dp)
                            .size(width = 48.dp, height = 64.dp)
                            .clip(RoundedCornerShape(12.dp)),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            coverInfo.title?.let { text ->
                Text(
                    text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(text),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        Color(
                            (if (preferDark) ImageUtils.parseColor(coverInfo.colorTitleDark) else ImageUtils.parseColor(coverInfo.colorTitle))
                                ?: 0xFFFFFFFF.toInt(),
                        ),
                    maxLines = 1,
                )
            }
            coverInfo.content?.let { text ->
                Text(
                    text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(text),
                    fontSize = 12.sp,
                    color =
                        Color(
                            (if (preferDark) ImageUtils.parseColor(coverInfo.colorContentDark) else ImageUtils.parseColor(coverInfo.colorContent))
                                ?: 0xFFDDDDDD.toInt(),
                        ),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            coverInfo.subContent?.let { text ->
                Text(
                    text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(text),
                    fontSize = 12.sp,
                    color =
                        Color(
                            (if (preferDark) ImageUtils.parseColor(coverInfo.colorSubContentDark) else ImageUtils.parseColor(coverInfo.colorSubContent))
                                ?: 0xFFDDDDDD.toInt(),
                        ),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Preview(name = "封面组件", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun CoverInfoComposePreview() {
    CoverInfoCompose(
        coverInfo = PreviewData.sampleCoverInfo,
        picMap = PreviewData.samplePicMap,
    )
}
