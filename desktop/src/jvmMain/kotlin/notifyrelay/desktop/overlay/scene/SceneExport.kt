package notifyrelay.desktop.overlay.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 场景导出标记。
 *
 * 只有录制矢量场景时才提供实例；叠加层其余渲染路径拿到 null，行为不变。
 * 动态元素据此改用哨兵色绘制，录制器识别后改写成接收端可自行推进的动画。
 */
internal val LocalSceneExport = staticCompositionLocalOf<SceneExport?> { null }

/** 一次导出期间可用的标记入口。 */
internal class SceneExport {
    /**
     * 频谱条的哨兵色，下标为条序。
     *
     * 取值避开可被缩写的组合（如 #FF0010 会被写成 #F01），
     * 否则录制器无法用固定正则识别；这里让三个通道都取两位有效值。
     */
    val spectrumColors: List<Color> =
        (0 until SPECTRUM_BAND_COUNT).map { Color(0xF1, 0x02, 0x30 + it) }

    /** 滚动文本的哨兵色；录制器据此给对应文本加上位移动画。 */
    val scrollColor: Color = Color(0xF1, 0x02, 0x40)
}

/** 频谱条数量；与 OverlaySpectrum 的绘制条数保持一致。 */
internal const val SPECTRUM_BAND_COUNT = 5

/** 哨兵色识别入口：录制器用它判断某个元素是否需要改写为动画。 */
internal object SceneSentinels {
    private val spectrumPrefix = 0x10
    private val scrollTag = 0x20

    /** 频谱条下标；非频谱哨兵色返回 null。 */
    fun spectrumIndex(
        red: Int,
        green: Int,
        blue: Int,
    ): Int? {
        if (red != 0xFF || green != 0x00) return null
        if (blue < spectrumPrefix || blue >= spectrumPrefix + SPECTRUM_BAND_COUNT) return null
        return blue - spectrumPrefix
    }

    /** 是否为滚动文本的哨兵色。 */
    fun isScroll(
        red: Int,
        green: Int,
        blue: Int,
    ): Boolean = red == 0xFF && green == 0x00 && blue == scrollTag
}

/** 在导出上下文里执行组合。 */
@Composable
internal fun ProvideSceneExport(
    export: SceneExport,
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalSceneExport provides export) {
        content()
    }
}
