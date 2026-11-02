package github.xzynine.superislandui.floating.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.util.lerp as lerpFloat

/** 展开时长：形态展开比收起需要更多时间。 */
private const val EXPAND_DURATION_MS = 460

/** 收起时长。 */
private const val COLLAPSE_DURATION_MS = 320

/** 展开始终先快后慢。 */
private val expandSpec = tween<Float>(durationMillis = EXPAND_DURATION_MS, easing = FastOutSlowInEasing)

/** 收起同样先快后慢，时长略短。 */
private val collapseSpec = tween<Float>(durationMillis = COLLAPSE_DURATION_MS, easing = FastOutSlowInEasing)

/** 默认展开态圆角。 */
private val DEFAULT_EXPANDED_CORNER_RADIUS = 16.dp

/** 折叠态圆角：大于任意胶囊高度，由形状裁切出端部半圆。 */
private val COLLAPSED_CORNER_RADIUS = 999.dp

/** 描边宽度。 */
private val BORDER_WIDTH = 1.dp

/** 描边颜色。 */
private val BORDER_COLOR = Color(0x80FFFFFF)

/** 形变外框阴影高度。 */
private val MORPH_SHADOW_ELEVATION = 6.dp

/**
 * 展开态与收起态之间的形态变换容器。
 *
 * 两态内容只被测量、不被切换组合：外框宽高由同一条形态变换进度同时插值到两态实测尺寸，
 * 宽度随高度一同收缩；圆角、背景色与描边由同一进度驱动，折叠时圆角增大到胶囊圆角、
 * 背景色从展开态颜色与透明度渐变到折叠态颜色与透明度。
 * 两态内容都以顶部中心为锚点放置，高度收缩时顶部边缘不动。
 *
 * 两个方向都按先快后慢的缓动推进，展开时长长于收起。
 *
 * 调用方负责把解析与回调提到本容器之外：两态内容始终参与测量，lambda 内重建状态会每帧重算。
 * 两态内容须关闭自身背景、圆角与描边，否则会出现两套外形叠加。
 *
 * @param expanded 目标态；变化时触发一次形变
 * @param expandedBackgroundColor 展开态背景色（含透明度），进度为 0 时生效
 * @param collapsedBackgroundColor 折叠态背景色（含透明度），进度为 1 时生效
 * @param modifier 施加于外框
 * @param expandedCornerRadius 展开态圆角；折叠过程中由该值逐步增大到胶囊圆角
 * @param expandedContent 展开态内容
 * @param collapsedContent 收起态内容
 */
@Composable
fun SuperIslandExpandableCard(
    expanded: Boolean,
    expandedBackgroundColor: Color,
    collapsedBackgroundColor: Color,
    modifier: Modifier = Modifier,
    expandedCornerRadius: Dp = DEFAULT_EXPANDED_CORNER_RADIUS,
    expandedContent: @Composable () -> Unit,
    collapsedContent: @Composable () -> Unit,
) {
    // 形态变换进度：0 为完全展开，1 为完全收起
    val collapseProgress by animateFloatAsState(
        targetValue = if (expanded) 0f else 1f,
        animationSpec = if (expanded) expandSpec else collapseSpec,
        label = "SuperIslandExpandableCard-progress",
    )

    val shape = RoundedCornerShape(lerp(expandedCornerRadius, COLLAPSED_CORNER_RADIUS, collapseProgress))
    val surfaceColor = lerpColor(expandedBackgroundColor, collapsedBackgroundColor, collapseProgress)

    Box(
        modifier =
            modifier
                .shadow(elevation = MORPH_SHADOW_ELEVATION, shape = shape)
                .clip(shape)
                .background(color = surfaceColor, shape = shape)
                .border(
                    width = BORDER_WIDTH,
                    color = BORDER_COLOR.copy(alpha = BORDER_COLOR.alpha * collapseProgress),
                    shape = shape,
                ),
    ) {
        MorphContent(
            expanded = expanded,
            progress = collapseProgress,
            expandedContent = expandedContent,
            collapsedContent = collapsedContent,
        )
    }
}

/**
 * 按形态变换进度布局两态内容。
 *
 * 宽度与高度取两态实测尺寸在同一进度下的插值，因此两者同步收缩而非各自独立过渡。
 * 两态内容同时叠放并以互补透明度交接；底色由外框按同一进度渐变。
 * 目标态始终绘制在顶层，淡出的旧态位于其下，不遮挡目标态的点击。
 *
 * @param expanded 目标态；决定两态的叠放次序
 */
@Composable
private fun MorphContent(
    expanded: Boolean,
    progress: Float,
    expandedContent: @Composable () -> Unit,
    collapsedContent: @Composable () -> Unit,
) {
    SubcomposeLayout { constraints ->
        // 两态都按宽松约束测量：折叠态宽度随内容收缩，展开态自行填满可用宽度
        val loose = constraints.copy(minWidth = 0, minHeight = 0)

        val expandedPlaceable =
            subcompose(MorphSlot.Expanded) {
                Box(Modifier.graphicsLayer { alpha = 1f - progress }) { expandedContent() }
            }.first().measure(loose)

        val collapsedPlaceable =
            subcompose(MorphSlot.Collapsed) {
                Box(Modifier.graphicsLayer { alpha = progress }) { collapsedContent() }
            }.first().measure(loose)

        val width = lerpFloat(expandedPlaceable.width.toFloat(), collapsedPlaceable.width.toFloat(), progress).roundToInt()
        val height = lerpFloat(expandedPlaceable.height.toFloat(), collapsedPlaceable.height.toFloat(), progress).roundToInt()

        layout(width, height) {
            // 顶部中心锚定：两态都贴顶居中，高度收缩时顶部边缘保持不动
            val expandedX = (width - expandedPlaceable.width) / 2
            val collapsedX = (width - collapsedPlaceable.width) / 2
            if (expanded) {
                collapsedPlaceable.place(collapsedX, 0)
                expandedPlaceable.place(expandedX, 0)
            } else {
                expandedPlaceable.place(expandedX, 0)
                collapsedPlaceable.place(collapsedX, 0)
            }
        }
    }
}

/** 形态变换的两态内容槽位。 */
private enum class MorphSlot {
    Expanded,
    Collapsed,
}
