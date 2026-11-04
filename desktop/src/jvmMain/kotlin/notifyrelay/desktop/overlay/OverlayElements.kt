package notifyrelay.desktop.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import notifyrelay.overlay.api.BalanceSnapshot
import notifyrelay.overlay.api.KeyboardSource
import notifyrelay.overlay.api.KeyboardStateSnapshot
import notifyrelay.overlay.api.OverlayStyle
import top.yukonga.miuix.kmp.basic.Text

/**
 * 时间元素。
 *
 * 文案由 DesktopClockSource 经 Store 推送，本组件只读不自行计时。
 * 文本为黑色描边层与彩色填充层叠放，无背景卡片。
 */
@Composable
internal fun ClockElement(
    style: OverlayStyle,
    text: String,
) {
    if (!style.enabled || text.isBlank()) return
    OutlinedText(text = text, style = style, fontSizePx = 48f)
}

/**
 * 键盘状态元素：按键盒 + 锁定键 + 映射提示。
 *
 * 蓝底白边圆角按键盒、每行最多 [KEY_MAX_PER_ROW] 个、提示行黑底显示在按键行下方；
 * Caps 开启显示「大写」，Num Lock 常态开启、仅关闭时显示「NumLk关」，Scroll Lock 开启时显示。
 */
@Composable
internal fun KeyboardElement(
    style: OverlayStyle,
    state: KeyboardStateSnapshot,
    hint: String,
    hintElapsedMs: Long,
) {
    val keys = keyboardSnapshotKeys(state)
    val hintOpacity = hintFadeOpacity(hint, hintElapsedMs)
    if (keys.isEmpty() && hintOpacity <= 0f) return

    val density = LocalDensity.current
    val scale = style.scale.coerceIn(0.5f, 4f)
    Column(
        verticalArrangement = Arrangement.spacedBy(with(density) { (KEY_BOX_MARGIN * scale).toDp() }),
    ) {
        if (keys.isNotEmpty()) {
            // 按格位序号换行，宽键不提前换行
            keys.chunked(KEY_MAX_PER_ROW).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(with(density) { (KEY_BOX_MARGIN * scale).toDp() }),
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

/** DeepSeek 余额元素。 */
@Composable
internal fun BalanceElement(
    style: OverlayStyle,
    snapshot: BalanceSnapshot,
) {
    if (!style.enabled) return
    val text =
        if (snapshot.balance == null) {
            "¥--"
        } else {
            "¥" + String.format("%.2f", snapshot.balance)
        }
    val change = changeLabel(snapshot.change)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedText(text = text, style = style, fontSizePx = 28f)
        if (change.isNotBlank()) {
            OutlinedText(
                text = change,
                style = style,
                fontSizePx = 14f,
                modifier = Modifier.padding(top = 2.dp),
            )
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

/** 单个按键盒：蓝底白边圆角，宽随文案、高固定 [KEY_BOX_SIZE]；缩放走元素外观。 */
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

/** 当前要显示的按键文案：全部按下键映射为键名，锁定键按原语义追加。 */
private fun keyboardSnapshotKeys(state: KeyboardStateSnapshot): List<String> {
    val keys =
        state.pressedKeys
            .map { KeyboardSource.keyName(it) }
            .distinct()
            .toMutableList()
    if (state.capsLockToggled) keys.add("大写")
    if (!state.numLockToggled) keys.add("NumLk关")
    if (state.scrollLockToggled) keys.add("ScrLk")
    return keys
}

/** 按键盒尺寸基准（像素，未缩放）。 */
private const val KEY_BOX_SIZE = 32f
private const val KEY_BOX_PADDING = 4f
private const val KEY_BOX_MARGIN = 2f
private const val KEY_BOX_RADIUS = 6f
private const val KEY_FONT_SIZE = 14f
private const val KEY_MAX_PER_ROW = 10
private const val KEY_CAP_OPACITY = 0.85f

/** 映射提示的展示时长（毫秒）。 */
internal const val KEYBOARD_HINT_DURATION_MS = 1_500L

/** 映射提示淡出不透明度：超时返回 0，最后 30% 时长线性淡出。 */
private fun hintFadeOpacity(
    hint: String,
    elapsedMs: Long,
): Float {
    if (hint.isBlank() || elapsedMs >= KEYBOARD_HINT_DURATION_MS) return 0f
    val remaining = 1f - elapsedMs.toFloat() / KEYBOARD_HINT_DURATION_MS
    return if (remaining < 0.3f) remaining / 0.3f else 1f
}

/**
 * 描边文本：黑色描边层与彩色填充层叠放。
 *
 * 两层必须包在 [Box] 内，否则父容器会把它们并排布局。
 */
@Composable
private fun OutlinedText(
    text: String,
    style: OverlayStyle,
    fontSizePx: Float,
    modifier: Modifier = Modifier,
) {
    val color = parseColor(style.colorHex)
    val scale = style.scale.coerceIn(0.5f, 4f)
    val textStyle =
        TextStyle(
            fontSize = (fontSizePx * scale).sp,
            fontWeight = FontWeight.Bold,
        )

    Box(modifier = modifier) {
        if (style.outlineWidth > 0f) {
            Text(
                text = text,
                color = Color.Black,
                style = textStyle.copy(drawStyle = Stroke(width = style.outlineWidth)),
            )
        }
        Text(text = text, color = color, style = textStyle)
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
