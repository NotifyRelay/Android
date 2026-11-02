package notifyrelay.desktop.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import notifyrelay.base.util.BatteryIconConverter
import notifyrelay.overlay.api.BalanceSnapshot
import notifyrelay.overlay.api.KeyboardSource
import notifyrelay.overlay.api.KeyboardStateSnapshot
import notifyrelay.overlay.api.LogiBatteryDevice
import notifyrelay.overlay.api.OverlayAnchor
import notifyrelay.shared.ui.common.deviceIconFontFamily
import top.yukonga.miuix.kmp.basic.Text
import java.awt.Toolkit
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

/**
 * 罗技电池浮窗：单列纵向卡片，每卡为电池图标 + 设备名。
 *
 * 卡片外观与 Windows 端 `LogiBatteryElement` 同口径：黑色 60% 圆角底、白色 35% 细边框、
 * 电量图标用 Segoe MDL2 字形并按分段着色（复用 [BatteryIconConverter]），设备名超长以省略号截断。
 */
@Composable
internal fun LogiBatteryElement(
    anchor: OverlayAnchor,
    devices: List<LogiBatteryDevice>,
    hideWhenDisconnected: Boolean,
) {
    val visible = devices.filter { !hideWhenDisconnected || it.online }
    if (visible.isEmpty()) return

    val density = LocalDensity.current
    val scale = anchor.scale.coerceIn(0.5f, 4f)
    // 卡片最大宽度：按屏宽比例，再夹到 120~540 像素（与 Windows 端一致）
    val cardMaxWidth =
        with(density) {
            (screenWidthPx() * LOGI_CARD_MAX_WIDTH_FACTOR)
                .coerceIn(LOGI_CARD_MIN_WIDTH_PX * scale, LOGI_CARD_MAX_WIDTH_PX * scale)
                .toDp()
        }
    val iconSize = with(density) { (LOGI_ICON_SIZE * scale).toSp() }
    val textSize = with(density) { (LOGI_TEXT_SIZE * scale).toSp() }
    val iconFontFamily = deviceIconFontFamily()

    OverlayFloatingElement(anchor) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(with(density) { (LOGI_CARD_SPACING * scale).toDp() }),
        ) {
            visible.forEach { device ->
                val shape = RoundedCornerShape(with(density) { (LOGI_CARD_RADIUS * scale).toDp() })
                Row(
                    modifier =
                        Modifier
                            .widthIn(max = cardMaxWidth)
                            .clip(shape)
                            .background(Color(0x99000000))
                            .border(
                                width = with(density) { (1f * scale).toDp() },
                                color = Color(0x59FFFFFF),
                                shape = shape,
                            ).padding(
                                horizontal = with(density) { (LOGI_CARD_PADDING_X * scale).toDp() },
                                vertical = with(density) { (LOGI_CARD_PADDING_Y * scale).toDp() },
                            ),
                    horizontalArrangement = Arrangement.spacedBy(with(density) { (LOGI_ICON_TEXT_GAP * scale).toDp() }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = logiBatteryGlyph(device),
                        color = logiBatteryColor(device),
                        fontSize = iconSize,
                        fontFamily = iconFontFamily,
                    )
                    Text(
                        text = device.deviceName,
                        color = Color(0xE6FFFFFF),
                        fontSize = textSize,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

/** 罗技设备电量图标；无电量时用满格未充电字形（与 Windows 端同口径）。 */
internal fun logiBatteryGlyph(device: LogiBatteryDevice): String =
    if (device.hasBattery && device.batteryPercent >= 0) {
        BatteryIconConverter.getBatteryIcon(device.batteryPercent, if (device.isCharging) '1' else '0')
    } else {
        BatteryIconConverter.getBatteryIcon(100, '0')
    }

/** 罗技设备电量图标颜色：无电量固定灰色，其余按电量分段着色。 */
internal fun logiBatteryColor(device: LogiBatteryDevice): Color =
    if (device.hasBattery && device.batteryPercent >= 0) {
        BatteryIconConverter.getBatteryColor(device.batteryPercent)
    } else {
        Color(0xFF808080)
    }

/** 主屏宽度（物理像素）。 */
private fun screenWidthPx(): Float =
    Toolkit
        .getDefaultToolkit()
        .screenSize.width
        .toFloat()

/** 罗技卡片的尺寸基准（未缩放前，单位为像素）。 */
private const val LOGI_ICON_SIZE = 20f
private const val LOGI_TEXT_SIZE = 13f
private const val LOGI_CARD_PADDING_X = 12f
private const val LOGI_CARD_PADDING_Y = 8f
private const val LOGI_CARD_SPACING = 6f
private const val LOGI_CARD_RADIUS = 7f
private const val LOGI_ICON_TEXT_GAP = 8f

/** 罗技卡片宽度的屏宽占比与像素上下限。 */
private const val LOGI_CARD_MAX_WIDTH_FACTOR = 0.35f
private const val LOGI_CARD_MIN_WIDTH_PX = 120f
private const val LOGI_CARD_MAX_WIDTH_PX = 540f

/**
 * 键盘状态浮窗：按键盒 + 锁定键 + 映射提示。
 *
 * 外观与 Windows 叠加层 `KeyboardElement` 同口径：蓝底白边圆角按键盒、
 * 每行最多 [KEY_MAX_PER_ROW] 个、提示行黑底显示在按键行下方；
 * 锁定键语义一致——Caps 开启显示「大写」，Num Lock 常态开启、仅关闭时显示「NumLk关」，
 * Scroll Lock 开启时显示。锚点定位、缩放与颜色沿用本端浮窗配置。
 */
@Composable
internal fun KeyboardElement(
    anchor: OverlayAnchor,
    state: KeyboardStateSnapshot,
    hint: String,
    hintElapsedMs: Long,
) {
    val keys = keyboardSnapshotKeys(state)
    val hintOpacity = hintFadeOpacity(hint, hintElapsedMs)
    if (keys.isEmpty() && hintOpacity <= 0f) return

    OverlayFloatingElement(anchor) {
        val density = LocalDensity.current
        val scale = anchor.scale.coerceIn(0.5f, 4f)
        Column(
            verticalArrangement =
                Arrangement.spacedBy(with(density) { (KEY_BOX_MARGIN * scale).toDp() }),
        ) {
            if (keys.isNotEmpty()) {
                // 与原版同口径：按格位序号换行，宽键不提前换行
                val rowSize = KEY_MAX_PER_ROW
                keys.chunked(rowSize).forEach { row ->
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(with(density) { (KEY_BOX_MARGIN * scale).toDp() }),
                    ) {
                        row.forEach { label -> KeyCap(label = label, scale = scale) }
                    }
                }
            }
            if (hintOpacity > 0f) {
                KeyCap(
                    label = hint,
                    scale = scale,
                    background = Color(0x99000000),
                    border = Color(0x80FFFFFF),
                    modifier = Modifier.alpha(hintOpacity),
                )
            }
        }
    }
}

/** 单个按键盒：蓝底白边圆角，宽随文案、高固定 [KEY_BOX_SIZE]；缩放与颜色走浮窗配置。 */
@Composable
private fun KeyCap(
    label: String,
    scale: Float,
    background: Color = Color(0xCC4DB3FF),
    border: Color = (Color.White.copy(alpha = 0.5f * KEY_CAP_OPACITY)),
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(with(density) { (KEY_BOX_RADIUS * scale).toDp() })
    Row(
        modifier =
            modifier
                .heightIn(min = with(density) { (KEY_BOX_SIZE * scale).toDp() })
                .clip(shape)
                .background(background)
                .border(
                    width = with(density) { (1.5f * scale).toDp() },
                    color = border,
                    shape = shape,
                ).padding(horizontal = with(density) { (KEY_BOX_PADDING * scale).toDp() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = KEY_CAP_OPACITY),
            fontSize = with(density) { (KEY_FONT_SIZE * scale).toSp() },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * 当前要显示的按键文案：全部按下键映射为键名（不过滤），锁定键按原版语义追加。
 */
private fun keyboardSnapshotKeys(state: KeyboardStateSnapshot): List<String> {
    val keys =
        state.pressedKeys
            .map { KeyboardSource.keyName(it) }
            .distinct()
            .toMutableList()
    // Caps 开启显示「大写」；Num Lock 常态开启、仅关闭时显示；Scroll Lock 开启时显示
    if (state.capsLockToggled) keys.add("大写")
    if (!state.numLockToggled) keys.add("NumLk关")
    if (state.scrollLockToggled) keys.add("ScrLk")
    return keys
}

/** 按键盒尺寸基准（像素，未缩放）：与 Windows 叠加层 KeyboardElement 一致。 */
private const val KEY_BOX_SIZE = 32f
private const val KEY_BOX_PADDING = 4f
private const val KEY_BOX_MARGIN = 2f
private const val KEY_BOX_RADIUS = 6f
private const val KEY_FONT_SIZE = 14f
private const val KEY_MAX_PER_ROW = 10
private const val KEY_CAP_OPACITY = 0.85f

/** 映射提示的展示时长（毫秒）；与 Windows 叠加层 HintTimeoutMs 一致。 */
internal const val KEYBOARD_HINT_DURATION_MS = 1_500L

/**
 * 映射提示淡出不透明度：超时返回 0，最后 30% 时长线性淡出。
 */
private fun hintFadeOpacity(
    hint: String,
    elapsedMs: Long,
): Float {
    if (hint.isBlank() || elapsedMs >= KEYBOARD_HINT_DURATION_MS) return 0f
    val remaining = 1f - elapsedMs.toFloat() / KEYBOARD_HINT_DURATION_MS
    return if (remaining < 0.3f) remaining / 0.3f else 1f
}

/** 时间浮窗：文案由 DesktopClockSource 经 Store 推送，本组件只读不自行计时。 */
@Composable
internal fun ClockElement(
    anchor: OverlayAnchor,
    text: String,
) {
    if (!anchor.enabled || text.isBlank()) return

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
    val scale = anchor.scale.coerceIn(0.5f, 4f)
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
