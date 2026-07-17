package com.xzyht.notifyrelay.sync

import notifyrelay.base.util.Logger
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import com.xzyht.notifyrelay.nativecore.NativeCore

/**
 * 统一加密发送器
 *
 * 通过 Rust sender queue 异步发送加密数据：
 * - 入队后由 Rust 侧处理加密、限流、重试和去重
 * - 返回后不保证立即发送成功
 */
object ProtocolSender {

    private const val TAG = "ProtocolSender"

    fun sendEncrypted(
        deviceManager: DeviceConnectionManager,
        target: DeviceInfo,
        header: String,
        plaintext: String,
        timeoutMs: Long = 10000L
    ) {
        val auth = deviceManager.authenticatedDevices[target.uuid]
        if (auth == null || !auth.isAccepted) return

        val queuePtr = NativeCore.senderQueuePtr
        if (queuePtr == 0L) {
            Logger.w(TAG, "发送队列未初始化，丢弃消息: $header -> ${target.displayName}")
            return
        }

        val ctx = deviceManager.rustContextInternal ?: return
        try {
            NativeCore.enqueueMessage(ctx, queuePtr, target.uuid, header, plaintext, null)
        } catch (e: Exception) {
            Logger.w(TAG, "入队失败 $header -> ${target.displayName}", e)
        }
    }
}
