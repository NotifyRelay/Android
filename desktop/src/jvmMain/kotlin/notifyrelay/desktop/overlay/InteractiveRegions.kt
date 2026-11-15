package notifyrelay.desktop.overlay

import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import java.awt.Rectangle
import kotlin.math.roundToInt

/**
 * 记录组件在窗口根坐标系中的位置与尺寸。
 *
 * 原生层按这些矩形决定点击穿透范围；组件不可交互时调用方不挂载本修饰符，
 * 或在回调内直接跳过（[enabled] 为 false 时不写入）。
 *
 * @param regions 区域集合，键为区域标识。
 * @param key 区域标识。
 * @param enabled 是否记录。
 */
fun Modifier.trackInteractiveRegion(
    regions: SnapshotStateMap<String, Rectangle>,
    key: String,
    enabled: Boolean = true,
): Modifier =
    onGloballyPositioned { coords ->
        if (!enabled) return@onGloballyPositioned
        val pos = coords.localToRoot(Offset.Zero)
        val size = coords.size
        regions[key] =
            Rectangle(
                pos.x.roundToInt(),
                pos.y.roundToInt(),
                size.width,
                size.height,
            )
    }
