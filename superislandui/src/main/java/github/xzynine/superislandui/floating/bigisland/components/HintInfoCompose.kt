package github.xzynine.superislandui.floating.bigisland.components

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.superislandui.common.PreviewData
import github.xzynine.superislandui.floating.common.ActionInfoButton
import github.xzynine.superislandui.floating.common.SuperIslandImageUtil
import github.xzynine.superislandui.floating.common.resolveIconUrl
import github.xzynine.superislandui.model.templates.HintInfo
import notifyrelay.core.util.image.ImageUtils

/**
 * 按钮组件2/3（hintInfo）Compose 实现
 * - 按钮组件2（type=2）：前置文本1/主要小文本1 + 前置文本2/主要小文本2 + 圆头图文按钮
 * - 按钮组件3（type=1）：主要文本 + 图文特殊标签（图标+文本+背景色）+ 圆头图文按钮
 * 顶部由系统统一提供的分割线（在宿主组合布局中绘制）
 */
@Composable
fun HintInfoCompose(
    hintInfo: HintInfo,
    picMap: Map<String, String>? = null,
) {
    val uiMode = LocalConfiguration.current.uiMode
    val preferDark = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun colorOf(
        light: String?,
        dark: String?,
        fallback: Int,
    ): Color = Color((if (preferDark) ImageUtils.parseColor(dark) else ImageUtils.parseColor(light)) ?: fallback)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hintInfo.type == 1) {
                // 按钮组件3：主要文本
                hintInfo.title?.let { text ->
                    Text(
                        text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(text),
                        color = colorOf(hintInfo.colorTitle, hintInfo.colorTitleDark, 0xFFFFFFFF.toInt()),
                        fontSize = 16.sp,
                        maxLines = 1,
                    )
                }
                // 图文特殊标签（图标 + 文本 + 背景色）
                hintInfo.content?.let { label ->
                    val labelBg = Color(ImageUtils.parseColor(hintInfo.colorContentBg) ?: 0x333482FF)
                    Row(
                        modifier =
                            Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(labelBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val iconUrl = resolveIconUrl(picMap, hintInfo.picContent, LocalContext.current)
                        if (!iconUrl.isNullOrEmpty()) {
                            SuperIslandImageUtil.rememberSuperIslandImagePainter(iconUrl)?.let {
                                Image(
                                    painter = it,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp).padding(end = 4.dp),
                                )
                            }
                        }
                        Text(
                            text = label,
                            color = colorOf(hintInfo.colorContent, hintInfo.colorContentDark, 0xFFFFFFFF.toInt()),
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }
            } else {
                // 按钮组件2：前置文本1 / 主要小文本1
                Column {
                    hintInfo.content?.let { text ->
                        Text(
                            text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(text),
                            color = colorOf(hintInfo.colorContent, hintInfo.colorContentDark, 0xFFDDDDDD.toInt()),
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                    hintInfo.title?.let { text ->
                        Text(
                            text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(text),
                            color = colorOf(hintInfo.colorTitle, hintInfo.colorTitleDark, 0xFFFFFFFF.toInt()),
                            fontSize = 16.sp,
                            maxLines = 1,
                        )
                    }
                }
                // 前置文本2 / 主要小文本2
                if (hintInfo.subContent != null || hintInfo.subTitle != null) {
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        hintInfo.subContent?.let { text ->
                            Text(
                                text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(text),
                                color = colorOf(hintInfo.colorSubContent, hintInfo.colorSubContentDark, 0xFFDDDDDD.toInt()),
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                        hintInfo.subTitle?.let { text ->
                            Text(
                                text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(text),
                                color = colorOf(hintInfo.colorSubTitle, hintInfo.colorSubTitleDark, 0xFFFFFFFF.toInt()),
                                fontSize = 16.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // 圆头图文按钮
        hintInfo.actionInfo?.let { action ->
            Spacer(modifier = Modifier.width(8.dp))
            ActionInfoButton(action = action, picMap = picMap)
        }
    }
}

@Preview(name = "按钮组件3", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun HintInfoComposeType1Preview() {
    HintInfoCompose(
        hintInfo = PreviewData.sampleHintInfo,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "按钮组件2", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun HintInfoComposeType2Preview() {
    HintInfoCompose(
        hintInfo = PreviewData.sampleHintInfoType2,
        picMap = PreviewData.samplePicMap,
    )
}
