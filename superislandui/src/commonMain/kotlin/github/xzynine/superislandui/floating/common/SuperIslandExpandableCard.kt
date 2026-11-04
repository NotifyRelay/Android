package github.xzynine.superislandui.floating.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** 收起态进度环的默认颜色（绿）。 */
private val DEFAULT_PROGRESS_COLOR = Color(0xFF34C759)

/** 收起态进度环线宽。 */
private val PROGRESS_STROKE_WIDTH = 2.dp

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
 * 给出 [collapsedProgress] 时，收起态的白色描边让位给沿胶囊轮廓的进度环：环自左侧圆弧
 * 下端起沿左侧圆弧向上、经顶边向右、再沿右侧圆弧向下绕行一周，按进度比例填充；
 * 描边与进度环由同一收起进度交替显隐，形态变换期间不出现两套轮廓同时可见。
 *
 * 调用方负责把解析与回调提到本容器之外：两态内容始终参与测量，lambda 内重建状态会每帧重算。
 * 两态内容须关闭自身背景、圆角与描边，否则会出现两套外形叠加。
 *
 * @param expanded 目标态；变化时触发一次形变
 * @param expandedBackgroundColor 展开态背景色（含透明度），进度为 0 时生效
 * @param collapsedBackgroundColor 折叠态背景色（含透明度），进度为 1 时生效
 * @param modifier 施加于外框
 * @param expandedCornerRadius 展开态圆角；折叠过程中由该值逐步增大到胶囊圆角
 * @param collapsedProgress 收起态进度 0~1；null 表示不画进度环，仍用白色描边
 * @param progressColor 进度环颜色
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
    collapsedProgress: Float? = null,
    progressColor: Color = DEFAULT_PROGRESS_COLOR,
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
    // 有进度环时，白色描边随收起进度淡出，把轮廓让给绿色进度环
    val borderAlpha =
        if (collapsedProgress == null) {
            BORDER_COLOR.alpha * collapseProgress
        } else {
            BORDER_COLOR.alpha * collapseProgress * (1f - collapseProgress)
        }

    // 外框尺寸由两态内容的实测尺寸决定，不随父约束撑满
    Box(modifier = modifier) {
        // 表面层负责底色、描边与内容裁切；进度环不放在这一层内，否则会被裁掉半条线宽
        Box(
            modifier =
                Modifier
                    .shadow(elevation = MORPH_SHADOW_ELEVATION, shape = shape)
                    .clip(shape)
                    .background(color = surfaceColor, shape = shape)
                    .border(
                        width = BORDER_WIDTH,
                        color = BORDER_COLOR.copy(alpha = borderAlpha),
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

        if (collapsedProgress != null) {
            Canvas(Modifier.matchParentSize()) {
                drawCapsuleProgress(
                    fraction = collapsedProgress,
                    alpha = collapseProgress,
                    color = progressColor,
                    strokeWidth = PROGRESS_STROKE_WIDTH.toPx(),
                )
            }
        }
    }
}

/**
 * 沿胶囊轮廓绘制进度环。
 *
 * 起点是左侧圆弧下端与底边的交点，绕行方向为「左侧圆弧向上 → 顶边向右 → 右侧圆弧向下 →
 * 底边向左」。路径按线宽内缩半个线宽，避免描边被画布边缘裁掉一半。
 *
 * @param fraction 进度比例 0~1
 * @param alpha 整体透明度；形态未收起时为 0
 * @param color 环颜色
 * @param strokeWidth 线宽（像素）
 */
private fun DrawScope.drawCapsuleProgress(
    fraction: Float,
    alpha: Float,
    color: Color,
    strokeWidth: Float,
) {
    if (alpha <= 0f || fraction <= 0f) return

    val inset = strokeWidth / 2f
    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset
    val height = bottom - top
    val radius = height / 2f
    val width = right - left
    // 宽度不足两端半圆时无法构成胶囊，放弃绘制
    if (width <= height || radius <= 0f) return

    val path =
        Path().apply {
            // 左侧圆弧下端
            moveTo(left + radius, bottom)
            // 左侧圆弧：自正下方经最左点转到正上方；角度递增即顺时针，须经过 180° 而非 0°
            arcTo(
                rect = Rect(left, top, left + 2f * radius, bottom),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false,
            )
            // 顶边向右
            lineTo(right - radius, top)
            // 右侧圆弧：自正上方经最右点转回正下方
            arcTo(
                rect = Rect(right - 2f * radius, top, right, bottom),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false,
            )
            close()
        }

    val measure = PathMeasure().apply { setPath(path, false) }
    val total = measure.length
    if (total <= 0f) return

    val segment = Path()
    measure.getSegment(0f, total * fraction.coerceIn(0f, 1f), segment, true)
    drawPath(
        path = segment,
        color = color.copy(alpha = alpha),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
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
