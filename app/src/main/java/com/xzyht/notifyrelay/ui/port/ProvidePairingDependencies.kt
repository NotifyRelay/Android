package com.xzyht.notifyrelay.ui.port

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
import com.xzyht.notifyrelay.nativecore.device.NativePairingCorePort
import notifyrelay.base.util.BatteryUtils
import notifyrelay.shared.feature.device.pairing.LocalPairingDependencies
import notifyrelay.shared.feature.device.pairing.PairingDependencies

/** 为 [content] 提供配对所需的平台依赖。 */
@Composable
fun ProvidePairingDependencies(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val deps =
        PairingDependencies(
            core = NativePairingCorePort,
            handshakePort = DeviceConnectionManagerSingleton.getHandshakeWaiters(context),
            batteryProvider = {
                val level = BatteryUtils.getBatteryLevel(context)
                if (BatteryUtils.isCharging(context)) level else -level
            },
        )
    CompositionLocalProvider(LocalPairingDependencies provides deps, content = content)
}
