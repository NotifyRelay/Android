package github.xzynine.superislandui.floating.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize

/** 形变起始与结束的缩放比：形变期间内容随之微缩，避免外框与内容脱节。 */
private const val EXPAND_SCALE = 0.86f

/** 形变弹簧阻尼比：略低于临界阻尼，收尾带轻微回弹。 */
private const val EXPAND_DAMPING_RATIO = 0.85f

/** 缩放弹簧。 */
private val expandScaleSpring =
    spring<Float>(
        dampingRatio = EXPAND_DAMPING_RATIO,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = 0.01f,
    )

/** 外框尺寸弹簧。 */
private val expandSizeSpring =
    spring<IntSize>(
        dampingRatio = EXPAND_DAMPING_RATIO,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntSize(1, 1),
    )

/**
 * 展开态与收起态之间的共享形变容器。
 *
 * 两态内容同时参与测量，外框宽度与高度按 [SizeTransform] 连续插值，切换期间外框不跳变、不交叉淡出。
 * 调用方负责把解析与回调提到本容器之外：形变期间两态内容都会被组合，lambda 内重建状态会每帧重算。
 *
 * @param expanded 目标态；变化时触发一次形变
 * @param modifier 施加于外框
 * @param expandedContent 展开态内容
 * @param collapsedContent 收起态内容
 */
@Composable
fun SuperIslandExpandableCard(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    expandedContent: @Composable () -> Unit,
    collapsedContent: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = expanded,
        modifier = modifier,
        transitionSpec = {
            (
                scaleIn(
                    animationSpec = expandScaleSpring,
                    initialScale = EXPAND_SCALE,
                ) togetherWith
                    scaleOut(
                        animationSpec = expandScaleSpring,
                        targetScale = EXPAND_SCALE,
                    )
            ).using(
                SizeTransform(
                    clip = true,
                    sizeAnimationSpec = { _, _ -> expandSizeSpring },
                ),
            )
        },
        contentAlignment = Alignment.Center,
        label = "SuperIslandExpandableCard",
    ) { isExpanded ->
        if (isExpanded) expandedContent() else collapsedContent()
    }
}
