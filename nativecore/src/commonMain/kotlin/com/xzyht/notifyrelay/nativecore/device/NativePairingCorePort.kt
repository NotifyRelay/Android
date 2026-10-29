package com.xzyht.notifyrelay.nativecore.device

import com.xzyht.notifyrelay.nativecore.NativeCore
import notifyrelay.base.log.Logger
import notifyrelay.domain.feature.device.pairing.PairingCorePort
import java.util.concurrent.atomic.AtomicInteger

/** 以 [NativeCore] 实现配对核心端口。 */
object NativePairingCorePort : PairingCorePort {
    private const val TAG = "PairingPort"

    /** 发起配对调用序号：用于排查同一进程内的重复发起。 */
    private val initCalls = AtomicInteger(0)

    override fun isReady(): Boolean = NativeCore.isReady()

    override fun generatePairingCode(ttlSecs: Int): String? = NativeCore.generatePairingCode(ttlSecs)

    override fun sendPairingInit(
        localUuid: String,
        remoteUuid: String,
        displayCode: String,
        battery: Int,
        platform: String,
    ): Int {
        val index = initCalls.incrementAndGet()
        Logger.i(
            TAG,
            "sendPairingInit #" + index + ": localUuid=" + localUuid + ", targetUuid=" + remoteUuid + ", codeLen=" + displayCode.length + ", platform=" + platform,
        )
        val result = NativeCore.sendPairingInit(localUuid, remoteUuid, displayCode, battery, platform)
        Logger.i(TAG, "sendPairingInit #" + index + " 返回: " + result)
        return result
    }

    override fun sendPairingResp(
        localUuid: String,
        localPublicKey: String,
        code: String,
        remoteIp: String,
        battery: Int,
        platform: String,
    ): Int = NativeCore.sendPairingResp(localUuid, localPublicKey, code, remoteIp, battery, platform)

    override fun clearPairingCode() {
        NativeCore.clearPairingCode()
    }
}
