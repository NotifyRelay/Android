package github.xzynine.superislandui.floating.bigisland.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.superislandui.common.PreviewData
import github.xzynine.superislandui.floating.common.ActionInfoButton
import github.xzynine.superislandui.floating.common.SuperIslandImageUtil
import github.xzynine.superislandui.model.templates.HighlightInfoV3
import notifyrelay.core.util.image.ImageUtils

/**
 * 按钮组件5（highlightInfoV3）Compose 实现（OS3 模板 17 / 18 / 19）
 * 高亮文本（primaryText）+ 补充文本（secondaryText，可划线）+ 文字标签（highLightText 圆角标签）
 * + 圆头图文按钮（actionInfo）
 */
@Composable
fun HighlightInfoV3Compose(
    highlightInfoV3: HighlightInfoV3,
    picMap: Map<String, String>? = null,
) {
    val uiMode = LocalConfiguration.current.uiMode
    val preferDark = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    val primaryColor =
        Color(
            (if (preferDark) ImageUtils.parseColor(highlightInfoV3.primaryColorDark) else ImageUtils.parseColor(highlightInfoV3.primaryColor))
                ?: 0xFFFFFFFF.toInt(),
        )
    val secondaryColor =
        Color(
            (if (preferDark) ImageUtils.parseColor(highlightInfoV3.secondaryColorDark) else ImageUtils.parseColor(highlightInfoV3.secondaryColor))
                ?: 0xFFDDDDDD.toInt(),
        )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧文本区：高亮文本 + 补充文本 + 文字标签（同一行）
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 高亮文本
            highlightInfoV3.primaryText?.let { text ->
                androidx.compose.material3.Text(
                    text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(text),
                    color = primaryColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            // 补充文本（可划线）
            highlightInfoV3.secondaryText?.let { text ->
                androidx.compose.material3.Text(
                    text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(text),
                    color = secondaryColor,
                    fontSize = 14.sp,
                    style =
                        if (highlightInfoV3.showSecondaryLine) {
                            TextStyle(textDecoration = TextDecoration.LineThrough)
                        } else {
                            TextStyle.Default
                        },
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            // 文字标签：圆角小标签（同一行）
            highlightInfoV3.highLightText?.let { label ->
                val labelTextColor =
                    Color(
                        (if (preferDark) ImageUtils.parseColor(highlightInfoV3.highLightTextColorDark) else ImageUtils.parseColor(highlightInfoV3.highLightTextColor))
                            ?: 0xFFFFFFFF.toInt(),
                    )
                val labelBgColor =
                    Color(
                        (if (preferDark) ImageUtils.parseColor(highlightInfoV3.highLightbgColorDark) else ImageUtils.parseColor(highlightInfoV3.highLightbgColor))
                            ?: 0x333482FF,
                    )
                androidx.compose.material3.Text(
                    text = label,
                    color = labelTextColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(labelBgColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        // 右侧圆头图文按钮
        highlightInfoV3.actionInfo?.let { action ->
            Spacer(modifier = Modifier.width(8.dp))
            ActionInfoButton(action = action, picMap = picMap)
        }
    }
}

@Preview(name = "按钮组件5", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun HighlightInfoV3ComposePreview() {
    HighlightInfoV3Compose(
        highlightInfoV3 = PreviewData.sampleHighlightInfoV3,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "按钮组件5-无图标", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun HighlightInfoV3ComposeNoIconPreview() {
    HighlightInfoV3Compose(
        highlightInfoV3 =
            PreviewData.sampleHighlightInfoV3.copy(
                actionInfo = PreviewData.sampleHighlightInfoV3.actionInfo?.copy(actionIcon = null),
            ),
        picMap = PreviewData.samplePicMap,
    )
}
