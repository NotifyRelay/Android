package com.xzyht.notifyrelay.feature.device

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.xzyht.notifyrelay.R
import notifyrelay.base.util.BatteryIconConverter
import notifyrelay.base.util.BatteryUtils
import notifyrelay.shared.ui.screen.devicelist.DeviceListPort

/** 以 [Context] 与 [LocalConfiguration] 实现 [DeviceListPort]。 */
class AndroidDeviceListPort(
    private val context: Context,
) : DeviceListPort {
    override fun localBatteryLevel(): Int = BatteryUtils.getBatteryLevel(context)

    override fun localChargingState(): Char = if (BatteryUtils.isCharging(context)) '1' else '0'

    override fun localBatteryGlyph(
        level: Int,
        chargingState: Char,
    ): String = BatteryIconConverter.getBatteryIcon(level, chargingState)

    override fun localBatteryColor(level: Int): Color = BatteryIconConverter.getBatteryColor(level)

    @Composable
    override fun isLandscape(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    @Composable
    override fun iconFontFamily(): FontFamily = FontFamily(Font(resId = R.font.segsmdl2))
}
