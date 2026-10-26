package com.xzyht.notifyrelay.feature.device.pairing

import com.xzyht.notifyrelay.nativecore.NativeCore
import notifyrelay.domain.feature.device.pairing.PairingCorePort

/** 以 [NativeCore] 实现配对核心端口。 */
object NativePairingCorePort : PairingCorePort {
    override fun isReady(): Boolean = NativeCore.isReady()

    override fun generatePairingCode(ttlSecs: Int): String? = NativeCore.generatePairingCode(ttlSecs)

    override fun sendPairingInit(
        localUuid: String,
        remoteUuid: String,
        displayCode: String,
        battery: Int,
        platform: String,
    ): Int = NativeCore.sendPairingInit(localUuid, remoteUuid, displayCode, battery, platform)

    override fun sendPairingResp(
        remoteUuid: String,
        localPublicKey: String,
        code: String,
        remoteIp: String,
        battery: Int,
        platform: String,
    ): Int = NativeCore.sendPairingResp(remoteUuid, localPublicKey, code, remoteIp, battery, platform)

    override fun clearPairingCode() {
        NativeCore.clearPairingCode()
    }
}
