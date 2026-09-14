package github.xzynine.superislandui.floating.bigisland.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.xzynine.superislandui.common.PreviewData
import github.xzynine.superislandui.floating.common.CommonImageCompose
import github.xzynine.superislandui.model.components.ProgressInfo
import notifyrelay.core.util.image.ImageUtils
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import kotlin.math.max

private const val BAR_HEIGHT_DP = 8
private const val CONTAINER_HEIGHT_DP = 48
private const val FORWARD_SIZE_DP = 40
private const val NODE_SIZE_DP = 32
private const val MIDDLE_POSITION_RATIO = 0.5f // 中间节点位置（相对进度条）

/**
 * 进度组件 Compose 实现
 * - 进度组件1：带前进图形（picForward）+ 中间节点（picMiddle/picMiddleUnselected）
 *   + 目标点（picEnd/picEndUnselected）的进度条
 * - 进度组件2：不含任何图标，即普通进度条
 */
@Composable
fun ProgressCompose(
    progressInfo: ProgressInfo,
    picMap: Map<String, String>?,
) {
    val hasNodes =
        !progressInfo.picForward.isNullOrEmpty() ||
            !progressInfo.picMiddle.isNullOrEmpty() ||
            !progressInfo.picMiddleUnselected.isNullOrEmpty() ||
            !progressInfo.picEnd.isNullOrEmpty() ||
            !progressInfo.picEndUnselected.isNullOrEmpty()

    // 进度组件2：无任何图标，仅进度条
    if (!hasNodes) {
        val progressColor = Color(ImageUtils.parseColor(progressInfo.colorProgress) ?: 0xFF00FF00.toInt())
        LinearProgressIndicator(
            progress = progressInfo.progress.toFloat() / 100f,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 0.dp, end = 0.dp, bottom = 0.dp),
            colors =
                ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = progressColor,
                ),
        )
        return
    }

    // 进度组件1：带前进图形/中间节点/目标点
    val progressColor = Color(ImageUtils.parseColor(progressInfo.colorProgress) ?: 0xFF3482FF.toInt())
    val trackColor =
        ImageUtils.parseColor(progressInfo.colorProgressEnd)?.let { Color(it) }
            ?: progressColor.copy(alpha = 0.3f)
    val progressValue = progressInfo.progress.coerceIn(0, 100)
    val ratio = progressValue / 100f

    var containerWidth by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CONTAINER_HEIGHT_DP.dp)
                .onSizeChanged { containerWidth = it.width.toFloat() },
    ) {
        // 进度条轨道
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(BAR_HEIGHT_DP.dp)
                    .align(Alignment.BottomCenter)
                    .background(trackColor, RoundedCornerShape(BAR_HEIGHT_DP.dp / 2)),
        )
        // 已完成进度
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(fraction = ratio)
                    .height(BAR_HEIGHT_DP.dp)
                    .align(Alignment.BottomStart)
                    .background(progressColor, RoundedCornerShape(BAR_HEIGHT_DP.dp / 2)),
        )

        // 前进图形：位于当前进度位置
        val forwardKey = progressInfo.picForward
        if (!forwardKey.isNullOrEmpty() && progressValue in 1..99) {
            val forwardSize = FORWARD_SIZE_DP.dp
            val safeContainerWidth = max(containerWidth, 1f)
            val forwardSizePx = with(density) { forwardSize.toPx() }
            val halfPx = forwardSizePx / 2f
            val centerPx = safeContainerWidth * ratio
            val clampedCenterPx = centerPx.coerceIn(halfPx, max(halfPx, safeContainerWidth - halfPx))
            val leftDp = with(density) { (clampedCenterPx - halfPx).toDp() }
            Box(
                modifier =
                    Modifier
                        .size(forwardSize)
                        .align(Alignment.BottomStart)
                        .offset(x = leftDp),
            ) {
                CommonImageCompose(
                    picKey = forwardKey,
                    picMap = picMap,
                    size = forwardSize,
                    isFocusIcon = false,
                    contentDescription = null,
                )
            }
        }

        // 中间节点：进度通过用 picMiddle，未通过用 picMiddleUnselected
        val middleKey =
            if (progressValue >= (MIDDLE_POSITION_RATIO * 100).toInt()) {
                progressInfo.picMiddle ?: progressInfo.picMiddleUnselected
            } else {
                progressInfo.picMiddleUnselected ?: progressInfo.picMiddle
            }
        if (!middleKey.isNullOrEmpty()) {
            val middleSize = NODE_SIZE_DP.dp
            val leftDp =
                with(density) {
                    (containerWidth * MIDDLE_POSITION_RATIO - middleSize.toPx() / 2f).coerceAtLeast(0f).toDp()
                }
            Box(
                modifier =
                    Modifier
                        .size(middleSize)
                        .align(Alignment.BottomStart)
                        .offset(x = leftDp),
            ) {
                CommonImageCompose(
                    picKey = middleKey,
                    picMap = picMap,
                    size = middleSize,
                    isFocusIcon = false,
                    contentDescription = null,
                )
            }
        }

        // 目标点：进度通过用 picEnd，未通过用 picEndUnselected
        val endKey =
            if (progressValue >= 100) {
                progressInfo.picEnd ?: progressInfo.picEndUnselected
            } else {
                progressInfo.picEndUnselected ?: progressInfo.picEnd
            }
        if (!endKey.isNullOrEmpty()) {
            val endSize = NODE_SIZE_DP.dp
            Box(
                modifier =
                    Modifier
                        .size(endSize)
                        .align(Alignment.BottomEnd),
            ) {
                CommonImageCompose(
                    picKey = endKey,
                    picMap = picMap,
                    size = endSize,
                    isFocusIcon = false,
                    contentDescription = null,
                )
            }
        }
    }
}

@Preview(name = "进度条60%", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun ProgressComposePreview() {
    ProgressCompose(
        progressInfo = PreviewData.sampleProgressInfo,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "进度条25%", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun ProgressComposeLowPreview() {
    ProgressCompose(
        progressInfo = PreviewData.sampleProgressInfoLow,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "进度组件1(带节点)", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun ProgressComposeWithNodesPreview() {
    ProgressCompose(
        progressInfo = PreviewData.sampleProgressInfoWithNodes,
        picMap = PreviewData.samplePicMap,
    )
}
