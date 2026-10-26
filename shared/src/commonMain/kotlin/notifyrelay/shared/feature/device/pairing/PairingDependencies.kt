package notifyrelay.shared.feature.device.pairing

import androidx.compose.runtime.staticCompositionLocalOf
import notifyrelay.domain.feature.device.pairing.PairingCorePort
import notifyrelay.domain.feature.device.service.contract.HandshakeWaiterPort

/** 配对所需的平台依赖。 */
data class PairingDependencies(
    val core: PairingCorePort,
    val handshakePort: HandshakeWaiterPort,
    /** 带符号电量（正=充电，负=放电）。 */
    val batteryProvider: () -> Int,
)

/** 由宿主在引导页根节点注入。 */
val LocalPairingDependencies = staticCompositionLocalOf<PairingDependencies?> { null }
