package github.xzynine.superislandui.floating.bigisland.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.superislandui.common.PreviewData
import github.xzynine.superislandui.floating.common.CommonImageCompose
import github.xzynine.superislandui.floating.common.SuperIslandImageUtil
import github.xzynine.superislandui.floating.common.formatTimerInfo
import github.xzynine.superislandui.model.components.TimerInfo
import github.xzynine.superislandui.model.templates.HighlightInfo
import kotlinx.coroutines.delay
import notifyrelay.core.util.image.ImageUtils

/**
 * 强调图文组件（highlightInfo）Compose 实现
 * 版式（MD 结构图）：强调文本 | 辅助文本1 | 功能图标 | 辅助文本2，同一行
 * 强调文本支持计时器
 */
@Composable
fun HighlightInfoCompose(
    highlightInfo: HighlightInfo,
    picMap: Map<String, String>?,
) {
    val density = LocalConfiguration.current.densityDpi / 160f
    val iconKey = selectIconKey(highlightInfo)
    val hasIcon = !iconKey.isNullOrEmpty()

    val primaryColor =
        ImageUtils.parseColor(highlightInfo.colorTitle)
            ?: ImageUtils.parseColor(highlightInfo.colorContent)
            ?: 0xFFFFFFFF.toInt()
    val primaryText =
        listOfNotNull(highlightInfo.title, highlightInfo.content, highlightInfo.subContent)
            .firstOrNull { it.isNotBlank() }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 强调文本（支持计时器）
        val timerInfo = highlightInfo.timerInfo
        if (timerInfo != null && !highlightInfo.iconOnly) {
            TimerText(timerInfo, primaryColor)
        } else {
            primaryText?.let {
                Text(
                    text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it),
                    color = Color(primaryColor),
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }

        // 辅助文本1（content）
        highlightInfo.content
            ?.takeIf { it.isNotBlank() && it != primaryText }
            ?.let {
                Text(
                    text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it),
                    color = Color(ImageUtils.parseColor(highlightInfo.colorContent) ?: 0xFFDDDDDD.toInt()),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

        // 功能图标（picFunction）
        if (hasIcon) {
            Spacer(modifier = Modifier.width(8.dp))
            CommonImageCompose(
                picKey = iconKey,
                picMap = picMap,
                size = (24 * density).dp,
                isFocusIcon = false,
                contentDescription = null,
            )
        }

        // 辅助文本2（subContent）
        highlightInfo.subContent
            ?.takeIf { it.isNotBlank() && it != primaryText }
            ?.let {
                Text(
                    text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it),
                    color = Color(ImageUtils.parseColor(highlightInfo.colorSubContent) ?: 0xFF9EA3FF.toInt()),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

        // 大岛区域左右图片（仅当显式提供时渲染，避免与功能图标重复）
        if (!highlightInfo.iconOnly) {
            val leftImageUrl = highlightInfo.bigImageLeft?.let { picMap?.get(it) }
            val rightImageUrl = highlightInfo.bigImageRight?.let { picMap?.get(it) }
            if (leftImageUrl != null || rightImageUrl != null) {
                Spacer(modifier = Modifier.width(8.dp))
                leftImageUrl?.let { SuperIslandImageUtil.rememberSuperIslandImagePainter(it) }?.let {
                    BigAreaImage(it, density)
                }
                rightImageUrl?.let { SuperIslandImageUtil.rememberSuperIslandImagePainter(it) }?.let {
                    BigAreaImage(it, density, showLeftMargin = true)
                }
            }
        }
    }
}

@Composable
private fun BigAreaImage(
    painter: Painter,
    density: Float,
    showLeftMargin: Boolean = false,
) {
    val size = (44 * density).dp
    val modifier = if (showLeftMargin) Modifier.padding(start = 6.dp) else Modifier

    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

@Composable
private fun TimerText(
    timerInfo: TimerInfo,
    colorInt: Int,
) {
    val displayState = remember(timerInfo) { mutableStateOf(formatTimerInfo(timerInfo)) }
    // 仅计时进行中（正计时 timerType=1 / 倒计时 timerType=-1）才每秒刷新，暂停/无效类型静态显示
    val isTimerRunning = timerInfo.timerType == 1 || timerInfo.timerType == -1
    LaunchedEffect(timerInfo) {
        if (!isTimerRunning) return@LaunchedEffect
        while (true) {
            displayState.value = formatTimerInfo(timerInfo)
            delay(1000)
        }
    }
    Text(
        text = displayState.value,
        fontSize = 20.sp,
        color = Color(colorInt),
        modifier = Modifier.padding(end = 4.dp),
    )
}

private fun selectIconKey(highlightInfo: HighlightInfo): String? {
    val candidates = mutableListOf<String>()
    candidates.add(highlightInfo.picFunction ?: "")
    candidates.add(highlightInfo.picFunctionDark ?: "")
    candidates.add(highlightInfo.bigImageLeft ?: "")
    candidates.add(highlightInfo.bigImageRight ?: "")
    return candidates.firstOrNull { it.isNotBlank() }
}

@Preview(name = "强调图文", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun HighlightInfoComposePreview() {
    HighlightInfoCompose(
        highlightInfo = PreviewData.sampleHighlightInfo,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "强调图文带计时器", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun HighlightInfoComposeWithTimerPreview() {
    HighlightInfoCompose(
        highlightInfo = PreviewData.sampleHighlightInfoWithTimer,
        picMap = PreviewData.samplePicMap,
    )
}
