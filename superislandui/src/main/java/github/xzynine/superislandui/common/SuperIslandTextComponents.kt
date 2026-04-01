package github.xzynine.superislandui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 自动适应文本组件
 * 当文本过长时，自动缩小字体大小，直到达到最小字体大小后换行
 *
 * @param text 要显示的文本内容
 * @param style 文本样式
 * @param color 文本颜色
 * @param minTextSize 最小字体大小，单位为sp，默认10f
 */
@Composable
fun AutoFitText(
    text: String,
    style: TextStyle,
    color: Color,
    minTextSize: Float = 10f
) {
    val initialSize = if (style.fontSize.isUnspecified) 14.sp else style.fontSize
    var currentSize by remember { mutableStateOf(initialSize) }
    var enableWrap by remember { mutableStateOf(false) }

    Text(
        text = text,
        color = color,
        style = style.copy(fontSize = currentSize),
        maxLines = if (enableWrap) Int.MAX_VALUE else 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout: TextLayoutResult ->
            if (!enableWrap && layout.hasVisualOverflow) {
                val newSizeSp = (currentSize.value * 0.92f)
                if (newSizeSp <= minTextSize) {
                    enableWrap = true
                } else {
                    currentSize = newSizeSp.sp
                }
            }
        }
    )
}

/**
 * 自动滚动文本组件
 * 支持自适应容器边界：
 * - 当文本长度未超过容器宽度时，文本不滚动，自适应撑开容器
 * - 当文本长度超过容器宽度时，自动实现滚动效果
 *
 * @param text 要显示的文本内容
 * @param style 文本样式
 * @param color 文本颜色
 * @param baseSpeedPxPerSec 基础滚动速度，单位为像素/秒，默认100f
 * @param pauseMillis 滚动开始前和滚动结束后的暂停时间，单位为毫秒，默认0
 * @param maxWidth 最大宽度限制，超过此宽度才会滚动，默认使用父容器宽度
 */
@Composable
fun AutoScrollText(
    text: String,
    style: TextStyle,
    color: Color,
    baseSpeedPxPerSec: Float = 100f,
    pauseMillis: Int = 0,
    maxWidth: Int? = null
) {
    // 预览模式下禁用动画，直接显示静态文本
    if (LocalInspectionMode.current) {
        Text(
            text = text,
            color = color,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    var parentWidth by remember { mutableIntStateOf(0) }
    val offset = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()

    val measured = textMeasurer.measure(AnnotatedString(text), style = style, maxLines = 1, softWrap = false)
    val intrinsicWidth = measured.size.width

    val actualMaxWidth = maxWidth ?: parentWidth
    val canScroll = actualMaxWidth > 0 && intrinsicWidth > actualMaxWidth
    val scrollRange = (intrinsicWidth - actualMaxWidth).coerceAtLeast(0)
    val speed = baseSpeedPxPerSec + (intrinsicWidth / 10f)
    val duration = if (canScroll) {
        (((scrollRange + actualMaxWidth) / speed) * 1000).toInt().coerceAtLeast(400)
    } else 0

    val intrinsicWidthDp = with(LocalDensity.current) { intrinsicWidth.toDp() }

    Box(
        modifier = if (canScroll) {
            Modifier
                .onGloballyPositioned { coordinates ->
                    if (maxWidth == null) {
                        parentWidth = coordinates.size.width
                    }
                }
                .clipToBounds()
        } else {
            Modifier
                .onGloballyPositioned { coordinates ->
                    if (maxWidth == null) {
                        parentWidth = coordinates.size.width
                    }
                }
        }
    ) {
        val textModifier = if (canScroll) {
            Modifier
                .width(intrinsicWidthDp)
                .graphicsLayer {
                    translationX = offset.value
                }
        } else {
            Modifier
        }
        
        Text(
            text = text,
            color = color,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = if (canScroll) TextOverflow.Visible else TextOverflow.Ellipsis,
            modifier = textModifier
        )
    }

    LaunchedEffect(canScroll, intrinsicWidth, actualMaxWidth, duration, pauseMillis) {
        offset.snapTo(0f)
        if (!canScroll) return@LaunchedEffect
        
        while (true) {
            delay(pauseMillis.toLong())
            offset.animateTo(
                -scrollRange.toFloat(),
                animationSpec = tween(durationMillis = duration, easing = LinearEasing)
            )
            offset.snapTo(0f)
            delay(300)
        }
    }
}
