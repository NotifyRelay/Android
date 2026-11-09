package github.xzynine.superislandui.floating.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.superislandui.common.AutoScrollText
import github.xzynine.superislandui.currentTimeMillis
import github.xzynine.superislandui.model.components.TimerInfo
import kotlinx.coroutines.delay
import notifyrelay.base.util.ColorUtils
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults

/**
 * 通用图片加载组件
 * 用于A区和B区的图片渲染
 */
@Composable
fun CommonImageCompose(
    picKey: String?,
    picMap: Map<String, String>?,
    size: Dp = 18.dp,
    isFocusIcon: Boolean = false,
    contentDescription: String? = null,
) {
    // 对于焦点图标，即使picKey为空，也尝试从picMap中获取
    if (!isFocusIcon && picKey.isNullOrBlank()) return

    // 获取当前主题
    val isDarkTheme = isSystemInDarkTheme()

    val iconUrl =
        if (isFocusIcon) {
            resolveFocusIconUrl(picMap, picKey, isDarkTheme)
        } else {
            resolveIconUrl(picMap, picKey)
        }

    // 预览模式下：只有在有有效URL时才显示占位符
    if (LocalInspectionMode.current) {
        if (iconUrl.isNullOrBlank()) return
        Box(
            modifier =
                Modifier
                    .size(size)
                    .background(Color.Gray.copy(alpha = 0.3f), CircleShape),
        )
        return
    }

    val painter = rememberSuperIslandImagePainter(iconUrl, picMap)

    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.size(size),
        )
    }
}

/**
 * 通用占位图片组件
 * 用于需要占位但不实际加载图片的场景
 */
@Composable
fun CommonImagePlaceholder(
    show: Boolean,
    size: Dp = 18.dp,
) {
    if (show) {
        Spacer(modifier = Modifier.size(size))
    }
}

/**
 * 通用文本块的Compose实现
 * 用于A区和B区的文本渲染
 */
@Composable
fun CommonTextBlockCompose(
    frontTitle: String?,
    title: String?,
    content: String?,
    narrow: Boolean,
    highlight: Boolean,
    monospace: Boolean,
    horizontalPadding: Dp = 6.dp,
    frontTitleColor: Color = Color(0xCCFFFFFF),
    frontTitleFontSize: TextUnit = 11.sp,
    titleColor: Color = if (highlight) Color(0xFF40C4FF) else Color.White,
    titleFontSize: TextUnit = 14.sp,
    contentColor: Color = Color(0xCCFFFFFF),
    contentFontSize: TextUnit = 12.sp,
    maxWidth: Dp = 160.dp, // 文本块宽度上限：短文本按内容宽，超宽进入滚动
) {
    // 确定字体家族
    val fontFamily =
        when {
            monospace -> FontFamily.Monospace
            narrow -> FontFamily.SansSerif
            else -> FontFamily.Default
        }

    Column(
        modifier =
            Modifier
                // 上限内自适应：短文本按内容宽，超宽文本进入 AutoScrollText 滚动
                .widthIn(max = maxWidth)
                .padding(start = horizontalPadding),
    ) {
        // 前置标题
        frontTitle?.let {
            AutoScrollText(
                text = it,
                style =
                    TextStyle(
                        fontFamily = fontFamily,
                        fontSize = frontTitleFontSize,
                    ),
                color = frontTitleColor,
                baseSpeedPxPerSec = 80f,
                pauseMillis = 800,
            )
        }

        // 主标题
        title?.let {
            AutoScrollText(
                text = it,
                style =
                    TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily,
                        fontSize = titleFontSize,
                    ),
                color = titleColor,
                baseSpeedPxPerSec = 100f,
                pauseMillis = 1000,
            )
        }

        // 内容
        content?.let {
            AutoScrollText(
                text = it,
                style =
                    TextStyle(
                        fontFamily = fontFamily,
                        fontSize = contentFontSize,
                    ),
                color = contentColor,
                baseSpeedPxPerSec = 130f, // 基础滚动速度，单位：px/秒
                pauseMillis = 200,
            )
        }
    }
}

/**
 * 圆形进度环的Compose实现
 * 使用Miuix的CircularProgressIndicator实现
 */
@Composable
fun CircularProgressCompose(
    progress: Int,
    colorReach: Color,
    colorUnReach: Color,
    strokeWidth: Dp,
    isClockwise: Boolean,
    size: Dp = 20.dp,
) {
    CircularProgressIndicator(
        progress = progress.coerceIn(0, 100).toFloat() / 100f,
        size = size,
        strokeWidth = strokeWidth,
        colors =
            ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = colorReach,
                backgroundColor = colorUnReach,
            ),
    )
}

/**
 * 安全解析不带 `#` 前缀的十六进制颜色文本；空白或非法输入返回 [default]。
 */
fun parseColorSafe(
    s: String?,
    default: Int,
): Int = ColorUtils.parseColorOr(s, default)

/**
 * 格式化计时器信息
 * 修复了计时逻辑，确保暂停时显示正确进度，解决正计时快3s问题
 */
fun formatTimerInfo(timer: TimerInfo): String {
    val now = currentTimeMillis()
    val timerType = timer.timerType
    val timerWhen = timer.timerWhen // 正计时：开始时间；倒计时：结束时间
    val timerSystemCurrent = timer.timerSystemCurrent // 计时状态变更时的系统时间

    val displayValue: Long =
        when (timerType) {
            -2 -> { // 倒计时暂停
                // 暂停状态：剩余时间 = 结束时间 - 状态变更时间
                val remaining = timerWhen - timerSystemCurrent
                remaining.coerceAtLeast(0)
            }
            -1 -> { // 倒计时进行中
                // 进行中状态：剩余时间 = 结束时间 - 当前时间
                val remaining = timerWhen - now
                remaining.coerceAtLeast(0)
            }
            2 -> { // 正计时暂停
                // 暂停状态：已过时间 = 状态变更时间 - 开始时间
                val elapsed = timerSystemCurrent - timerWhen
                elapsed.coerceAtLeast(0)
            }
            1 -> { // 正计时进行中
                // 进行中状态：已过时间 = 当前时间 - 开始时间
                val elapsed = now - timerWhen
                elapsed.coerceAtLeast(0)
            }
            else -> 0
        }

    val seconds = (displayValue / 1000).toInt()
    return formatTimeFromSeconds(seconds)
}

private fun formatTimeFromSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        "${twoDigits(hours)}:${twoDigits(minutes % 60)}:${twoDigits(seconds % 60)}"
    } else {
        "${twoDigits(minutes % 60)}:${twoDigits(seconds % 60)}"
    }
}

/**
 * 两位补零，等价于 %02d（value 超过两位时不截断）。
 */
private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

/**
 * 格式化时长
 */
fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000).toInt()
    return formatTimeFromSeconds(seconds)
}

/**
 * 超级岛根布局组件
 * 提供统一的卡片样式和布局结构
 *
 * @param drawSurface 是否自行绘制卡片背景与圆角；由外部形态变换容器统一绘制表面时传 false
 */
@Composable
fun SuperIslandComposeRoot(
    content: @Composable () -> Unit,
    isOverlapping: Boolean = false,
    drawSurface: Boolean = true,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
    ) {
        if (drawSurface) {
            Card(
                cornerRadius = SUPER_ISLAND_CARD_CORNER_RADIUS,
                colors = CardDefaults.defaultColors(color = superIslandCardColor(isOverlapping)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                ) {
                    content()
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(8.dp),
            ) {
                content()
            }
        }
    }
}

/** 展开态卡片圆角。 */
val SUPER_ISLAND_CARD_CORNER_RADIUS = 16.dp

/**
 * 超级岛卡片的表面色：重叠提示态为红色，常态为黑色。
 *
 * 形态变换容器以本函数返回值作唯一背景色，保证展开与收起共用同一底色。
 */
fun superIslandCardColor(isOverlapping: Boolean): Color =
    if (isOverlapping) {
        Color.Red.copy(alpha = 0.92f)
    } else {
        Color.Black.copy(alpha = 0.92f)
    }

/**
 * 计时器文本随时间刷新。
 *
 * 仅计时进行中（正计时 `timerType=1` / 倒计时 `timerType=-1`）每秒重算，暂停与无效类型保持首次格式化的结果。
 *
 * @param timerInfo 计时器信息。
 * @returns 随时间更新的格式化文本。
 */
@Composable
fun rememberTimerText(timerInfo: TimerInfo): String {
    val displayState = remember(timerInfo) { mutableStateOf(formatTimerInfo(timerInfo)) }
    val isTimerRunning = timerInfo.timerType == 1 || timerInfo.timerType == -1
    LaunchedEffect(timerInfo) {
        if (!isTimerRunning) return@LaunchedEffect
        while (true) {
            displayState.value = formatTimerInfo(timerInfo)
            delay(1000)
        }
    }
    return displayState.value
}
