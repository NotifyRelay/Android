package com.xzyht.notifyrelay.sync

import com.xzyht.notifyrelay.feature.device.model.DeviceInfo
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.nativecore.NativeCore
import notifyrelay.base.util.Logger

/**
 * 统一加密发送器
 *
 * 通过 Rust sender queue 异步发送加密数据：
 * - 入队后由 Rust 侧处理加密、限流、重试和去重
 * - 返回后不保证立即发送成功
 */
object ProtocolSender {
    private const val TAG = "ProtocolSender"

    /** 入队结果 */
    enum class EnqueueResult { SUCCESS, DEDUPED, QUEUE_UNINITIALIZED, MISSING_CONTEXT, AUTH_FAILED, NATIVE_ERROR }

    fun sendEncrypted(
        deviceManager: DeviceConnectionManager,
        target: DeviceInfo,
        header: String,
        plaintext: String,
        timeoutMs: Long = 10000L,
    ): EnqueueResult {
        val auth =
            synchronized(deviceManager.authenticatedDevices) {
                deviceManager.authenticatedDevices[target.uuid]
            }
        if (auth == null || !auth.isAccepted) return EnqueueResult.AUTH_FAILED

        val queuePtr = NativeCore.senderQueuePtr
        if (queuePtr == 0L) {
            Logger.w(TAG, "发送队列未初始化，丢弃消息: $header -> ${target.displayName}")
            return EnqueueResult.QUEUE_UNINITIALIZED
        }

        val ctx = NativeCore.getContext() ?: return EnqueueResult.MISSING_CONTEXT
        return try {
            when (NativeCore.enqueueMessage(ctx, queuePtr, target.uuid, header, plaintext)) {
                1 -> EnqueueResult.SUCCESS
                0 -> EnqueueResult.DEDUPED
                else -> EnqueueResult.NATIVE_ERROR
            }
        } catch (e: Exception) {
            Logger.w(TAG, "入队失败 $header -> ${target.displayName}", e)
            EnqueueResult.NATIVE_ERROR
        }
    }
}
