package notifyrelay.shared.ui.screen.devicelist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import notifyrelay.base.util.BatteryIconConverter
import notifyrelay.shared.ui.common.deviceIconFontFamily

/**
 * 设备列表页所需的本机信息。
 *
 * 电量、充电状态与电池图标字形均属平台能力，由宿主实现后注入；
 * 页面本身只做展示。
 */
interface DeviceListPort {
    /**
     * 本机电量（0-100）。
     *
     * 在组合期内调用：实现方读取平台侧的可观察状态即可让页面随电量变化重组。
     */
    fun localBatteryLevel(): Int

    /** 充电状态字符：'1' 充电 / '0' 未充电；与 [localBatteryLevel] 同为组合期内调用。 */
    fun localChargingState(): Char

    /** 本机电池图标字形（MDL2 私有区字符）；两平台同源，默认实现即可。 */
    fun localBatteryGlyph(
        level: Int,
        chargingState: Char,
    ): String = BatteryIconConverter.getBatteryIcon(level, chargingState)

    /** 本机电池图标颜色；两平台同源，默认实现即可。 */
    fun localBatteryColor(level: Int): Color = BatteryIconConverter.getBatteryColor(level)

    /** 当前是否横屏。 */
    @Composable
    fun isLandscape(): Boolean

    /** 列表使用的图标字体族；字体由 `:shared` 提供，两平台同源。 */
    @Composable
    fun iconFontFamily(): FontFamily? = deviceIconFontFamily()
}

/** 由宿主提供实现。 */
val LocalDeviceListPort = staticCompositionLocalOf<DeviceListPort?> { null }
