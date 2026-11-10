package notifyrelay.desktop.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 频谱条数量：与 Windows 叠加层收起胶囊一致。 */
private const val SPECTRUM_BANDS = 5

/** 频谱条间距占比：原版 BarWidth 2.5 / BarGap 2 的比值。 */
private const val SPECTRUM_BAR_GAP = 2f / (2.5f + 2f)

/** 波峰条位（原版 bar 1 与 bar 3，零基）。 */
private const val SPECTRUM_PEAK_A = 1f
private const val SPECTRUM_PEAK_B = 3f

/** 离波峰每远一格衰减的幅度（原版 0.35：峰=1.0、中=0.65、边=0.35）。 */
private const val SPECTRUM_PEAK_DECAY = 0.35f

/** 震荡速度（原版每秒 3.5 相位）。 */
private const val SPECTRUM_PHASE_PER_SEC = 3.5

/** 每条独立相位偏移（原版 i * 1.1）。 */
private const val SPECTRUM_PHASE_STEP = 1.1f

/** 震荡下限与摆幅（原版 0.55 + 0.45 * sin）。 */
private const val SPECTRUM_OSC_BASE = 0.55f
private const val SPECTRUM_OSC_SWING = 0.45f

/** 暂停时的残留高度占比：不收成零，保留一条细底以指示元素仍在。 */
private const val SPECTRUM_PAUSED_FACTOR = 0.08f

/** 起停过渡时长（毫秒）：暂停与恢复都走一次短缓动，避免瞬间跳变。 */
private const val SPECTRUM_TRANSITION_MS = 220

/** 频谱条颜色。 */
private val SPECTRUM_COLOR = Color(0xFFFFFFFF)

/**
 * 频谱条动画：双波峰 W 形震荡（对齐 Windows 叠加层 MediaCard.PaintBars）。
 *
 * 波峰位于第 2、4 条，离波峰越远基础高度越低；每条带独立相位正弦摆动，
 * 峰值条可达区域满高。数据为合成波形，不接音频采集。
 *
 * 暂停时全部收起到 [SPECTRUM_PAUSED_FACTOR] 的残留高度，恢复播放再涨回震荡高度；
 * 两个方向都按 [SPECTRUM_TRANSITION_MS] 缓动，不逐帧跳变。
 *
 * @param positionMs 当前时间戳（毫秒），驱动动画
 * @param playing 是否正在播放；为 false 时收起到残留高度
 * @param widthDp 频谱区域宽度
 * @param heightDp 频谱区域高度
 */
@Composable
internal fun OverlaySpectrum(
    positionMs: Long,
    playing: Boolean,
    widthDp: Float = 28f,
    heightDp: Float = 80f,
) {
    val amplitude by animateFloatAsState(
        targetValue = if (playing) 1f else SPECTRUM_PAUSED_FACTOR,
        animationSpec = tween(durationMillis = SPECTRUM_TRANSITION_MS),
        label = "OverlaySpectrum-amplitude",
    )

    Canvas(modifier = Modifier.size(widthDp.dp, heightDp.dp)) {
        val barSlot = size.width / SPECTRUM_BANDS
        val barWidth = barSlot * (1f - SPECTRUM_BAR_GAP)
        val gap = barSlot * SPECTRUM_BAR_GAP
        // 相位用 Double 计算：毫秒时间戳转 Float 的量化步长超过帧间隔，会冻结动画
        val phase = positionMs / 1000.0 * SPECTRUM_PHASE_PER_SEC
        for (i in 0 until SPECTRUM_BANDS) {
            // 离最近波峰的距离决定基础高度：峰=1.0、中=0.65、边=0.35
            val dist = min(abs(i - SPECTRUM_PEAK_A), abs(i - SPECTRUM_PEAK_B))
            val baseFactor = 1f - dist * SPECTRUM_PEAK_DECAY
            val osc = (SPECTRUM_OSC_BASE + SPECTRUM_OSC_SWING * sin(phase + i * SPECTRUM_PHASE_STEP)).toFloat()
            // 残留高度不受波峰位置影响：暂停时各条齐平
            val heightFactor = SPECTRUM_PAUSED_FACTOR + (baseFactor * osc - SPECTRUM_PAUSED_FACTOR) * amplitude
            val barHeight = max(1f, size.height * heightFactor)
            val x = i * barSlot + gap / 2f
            val y = (size.height - barHeight) / 2f
            drawRect(
                color = SPECTRUM_COLOR,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
            )
        }
    }
}
