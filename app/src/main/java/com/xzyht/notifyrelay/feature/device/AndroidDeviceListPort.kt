package com.xzyht.notifyrelay.feature.device

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import notifyrelay.base.util.BatteryUtils
import notifyrelay.shared.ui.screen.devicelist.DeviceListPort

/** 以 [Context] 与 [LocalConfiguration] 实现 [DeviceListPort]。 */
class AndroidDeviceListPort(
    private val context: Context,
) : DeviceListPort {
    override fun localBatteryLevel(): Int = BatteryUtils.getBatteryLevel(context)

    @Composable
    override fun isLandscape(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
}
