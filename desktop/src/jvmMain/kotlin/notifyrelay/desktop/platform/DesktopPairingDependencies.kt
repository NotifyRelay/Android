package notifyrelay.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.xzyht.notifyrelay.nativecore.device.NativePairingCorePort
import notifyrelay.domain.feature.device.service.pairing.HandshakeWaiterRegistry
import notifyrelay.shared.feature.device.pairing.LocalPairingDependencies
import notifyrelay.shared.feature.device.pairing.PairingDependencies

/**
 * 桌面端配对依赖。
 *
 * 与 Android 侧 `ProvidePairingDependencies` 同构：核心能力走 [NativePairingCorePort]，
 * 握手等待器由设备管理器注入（与 core 回调侧的 `handshakeWaiters` 为同一实例），
 * 电量取 [BatteryLevel.signedPercent]（正=充电、负=放电），平台标识为 `pc`。
 */
@Composable
fun ProvideDesktopPairingDependencies(
    handshakeWaiter: HandshakeWaiterRegistry,
    content: @Composable () -> Unit,
) {
    val deps =
        PairingDependencies(
            core = NativePairingCorePort,
            handshakePort = handshakeWaiter,
            batteryProvider = { BatteryLevel.signedPercent() },
            platform = PLATFORM,
        )
    CompositionLocalProvider(LocalPairingDependencies provides deps, content = content)
}

/** 桌面端在协议中的平台标识。 */
private const val PLATFORM = "pc"
