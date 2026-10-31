package notifyrelay.desktop.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import notifyrelay.overlay.api.BalanceSnapshot
import notifyrelay.overlay.api.KeyboardSource
import notifyrelay.overlay.api.KeyboardStateSnapshot
import notifyrelay.overlay.api.LogiBatteryDevice
import notifyrelay.overlay.api.OverlayAnchor
import top.yukonga.miuix.kmp.basic.Text
import java.awt.Toolkit
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 叠加层浮窗元素渲染：余额、罗技电池、键盘状态、时间。
 *
 * 定位与 C# 叠加层的 `Align(center, x%, y%)` 同口径：
 * 锚点百分比换算为主屏物理像素，元素以该点为中心；测量到尺寸后回退半个自身宽高居中。
 * 文本为「黑色描边 + 彩色填充」，窗口点击穿透，无背景卡片。
 */
@Composable
internal fun OverlayFloatingElement(
    anchor: OverlayAnchor,
    content: @Composable () -> Unit,
) {
    if (!anchor.enabled) return

    val screen = remember { Toolkit.getDefaultToolkit().screenSize }
    var size by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            modifier =
                Modifier
                    // 先测量内容自身尺寸，再按「锚点为中心」回退半个宽高定位
                    .onSizeChanged { size = Size(it.width.toFloat(), it.height.toFloat()) }
                    .offset {
                        val centerX = screen.width * anchor.xPercent / 100f
                        val centerY = screen.height * anchor.yPercent / 100f
                        IntOffset(
                            (centerX - size.width / 2f).roundToInt(),
                            (centerY - size.height / 2f).roundToInt(),
                        )
                    },
        ) {
            content()
        }
    }
}

/** DeepSeek 余额浮窗。 */
@Composable
internal fun BalanceElement(
    anchor: OverlayAnchor,
    snapshot: BalanceSnapshot,
) {
    val text =
        if (snapshot.balance == null) {
            "¥--"
        } else {
            "¥" + String.format("%.2f", snapshot.balance)
        }
    val change = changeLabel(snapshot.change)
    OverlayFloatingElement(anchor) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedText(text = text, anchor = anchor, fontSizePx = 28f)
            if (change.isNotBlank()) {
                OutlinedText(
                    text = change,
                    anchor = anchor,
                    fontSizePx = 14f,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** 余额变化文案；未变化时为空串。 */
private fun changeLabel(change: Double): String =
    when {
        change > 0.0001 -> "▲ " + String.format("%.2f", change)
        change < -0.0001 -> "▼ " + String.format("%.2f", -change)
        else -> ""
    }

/** 罗技电池浮窗：逐设备一行，显示电量与设备名。 */
@Composable
internal fun LogiBatteryElement(
    anchor: OverlayAnchor,
    devices: List<LogiBatteryDevice>,
    hideWhenDisconnected: Boolean,
) {
    val visible = devices.filter { !hideWhenDisconnected || it.online }
    if (visible.isEmpty()) return

    OverlayFloatingElement(anchor) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            visible.forEach { device ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedText(text = deviceBatteryLabel(device), anchor = anchor, fontSizePx = 16f)
                    Spacer(Modifier.width(6.dp))
                    OutlinedText(text = device.deviceName, anchor = anchor, fontSizePx = 16f)
                }
            }
        }
    }
}

/** 电池文案：电量百分比 + 充电标记；无电量时为占位符。 */
private fun deviceBatteryLabel(device: LogiBatteryDevice): String =
    when {
        !device.hasBattery || device.batteryPercent < 0 -> "—"
        device.isCharging -> device.batteryPercent.toString() + "% ⚡"
        else -> device.batteryPercent.toString() + "%"
    }

/** 键盘状态浮窗：有映射提示时显示提示，否则显示按键与锁定键。 */
@Composable
internal fun KeyboardElement(
    anchor: OverlayAnchor,
    state: KeyboardStateSnapshot,
    hint: String,
) {
    val text = if (hint.isNotBlank()) hint else keyboardLabel(state)
    if (text.isBlank()) return

    OverlayFloatingElement(anchor) {
        OutlinedText(text = text, anchor = anchor, fontSizePx = 18f)
    }
}

/** 按键文案：按下键按 `+` 连接，锁定键追加；两者均无时返回空串。 */
private fun keyboardLabel(state: KeyboardStateSnapshot): String {
    val parts = mutableListOf<String>()
    if (state.pressedKeys.isNotEmpty()) {
        parts.add(state.pressedKeys.joinToString(" + ") { KeyboardSource.keyName(it) })
    }
    val toggles =
        buildList {
            if (state.capsLockToggled) add("Caps")
            if (state.numLockToggled) add("Num")
            if (state.scrollLockToggled) add("Scroll")
        }
    if (toggles.isNotEmpty()) parts.add(toggles.joinToString(" · ") + " 锁")
    return parts.joinToString("　")
}

/** 时间浮窗：秒级刷新；关闭秒显示时对齐到分钟边界，避免无谓重组。 */
@Composable
internal fun ClockElement(
    anchor: OverlayAnchor,
    showSeconds: Boolean,
    use24Hour: Boolean,
) {
    if (!anchor.enabled) return

    val formatter =
        remember(showSeconds, use24Hour) {
            val pattern =
                when {
                    showSeconds && use24Hour -> "HH:mm:ss"
                    showSeconds -> "hh:mm:ss a"
                    use24Hour -> "HH:mm"
                    else -> "hh:mm a"
                }
            DateTimeFormatter.ofPattern(pattern)
        }

    var text by remember { mutableStateOf(LocalTime.now().format(formatter)) }
    LaunchedEffect(formatter) {
        while (true) {
            text = LocalTime.now().format(formatter)
            // 无秒时等到下一分钟边界再刷新，减少无意义重组
            val waitMs = if (showSeconds) 1_000L else (60L - LocalTime.now().second) * 1_000L
            delay(waitMs.coerceAtLeast(200L))
        }
    }

    OverlayFloatingElement(anchor) {
        OutlinedText(text = text, anchor = anchor, fontSizePx = 48f)
    }
}

/**
 * 描边文本：黑色描边层与彩色填充层叠放。
 *
 * 两层必须包在 [Box] 内，否则父容器会把它们并排布局
 * （与弹幕文字同一处理方式）。
 */
@Composable
private fun OutlinedText(
    text: String,
    anchor: OverlayAnchor,
    fontSizePx: Float,
    modifier: Modifier = Modifier,
) {
    val color = parseColor(anchor.colorHex)
    val scale = anchor.scale.coerceIn(0.5f, 3f)
    val style =
        TextStyle(
            fontSize = (fontSizePx * scale).sp,
            fontWeight = FontWeight.Bold,
        )

    Box(modifier = modifier) {
        if (anchor.outlineWidth > 0f) {
            Text(
                text = text,
                color = Color.Black,
                style = style.copy(drawStyle = Stroke(width = anchor.outlineWidth)),
            )
        }
        Text(text = text, color = color, style = style)
    }
}

/** 解析 `#RRGGBB` 或 `#AARRGGBB`；非法值回落白色。 */
private fun parseColor(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    val value = cleaned.toLongOrNull(16) ?: return Color.White
    return when (cleaned.length) {
        6 -> Color(0xFF000000L or value)
        8 -> Color(value)
        else -> Color.White
    }
}
