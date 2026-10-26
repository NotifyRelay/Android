package notifyrelay.shared.ui.screen.devicelist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** 设备列表页所需的本机信息。 */
interface DeviceListPort {
    /** 本机电量（0-100）。 */
    fun localBatteryLevel(): Int

    /** 当前是否横屏。 */
    @Composable
    fun isLandscape(): Boolean
}

/** 由宿主提供实现。 */
val LocalDeviceListPort = staticCompositionLocalOf<DeviceListPort?> { null }
