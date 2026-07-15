package com.xzyht.notifyrelay.sync

import notifyrelay.base.util.Logger
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 统一加密发送器
 *
 * 封装加密、认证检查、TCP发送与报文头拼装：
 * 最终报文格式：`<HEADER>:<localUuid>:<localPublicKey>:<encryptedPayload>\n`
 */
object ProtocolSender {

    private const val TAG = "ProtocolSender"
    private const val DEFAULT_TIMEOUT = 10000L

    fun sendEncrypted(
        deviceManager: DeviceConnectionManager,
        target: DeviceInfo,
        header: String,
        plaintext: String,
        timeoutMs: Long = DEFAULT_TIMEOUT
    ) {
        val auth = deviceManager.authenticatedDevices[target.uuid]
        if (auth == null || !auth.isAccepted) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = deviceManager.encryptData(plaintext, target.uuid, header)
                OneShotTcpClient.sendOnly(target.ip, target.port, payload, timeoutMs.toInt())
            } catch (e: Exception) {
                Logger.w(TAG, "发送失败 $header -> ${target.displayName}", e)
            }
        }
    }
}
