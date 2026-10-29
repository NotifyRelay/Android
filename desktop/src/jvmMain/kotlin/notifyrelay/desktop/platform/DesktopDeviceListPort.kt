package notifyrelay.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import notifyrelay.base.util.BatteryIconConverter
import notifyrelay.shared.ui.screen.devicelist.DeviceListPort

/**
 * 桌面端设备列表端口。
 *
 * 电量固定为满值、不充电，字体使用默认族；横屏口径与主界面端口一致（窗口宽高比）。
 */
class DesktopDeviceListPort : DeviceListPort {
    override fun localBatteryLevel(): Int = 100

    override fun localChargingState(): Char = '0'

    override fun localBatteryGlyph(
        level: Int,
        chargingState: Char,
    ): String = BatteryIconConverter.getBatteryIcon(level, chargingState)

    override fun localBatteryColor(level: Int): Color = BatteryIconConverter.getBatteryColor(level)

    @Composable
    override fun isLandscape(): Boolean = isWindowLandscape()

    @Composable
    override fun iconFontFamily(): FontFamily? = null
}
