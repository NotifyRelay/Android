package com.xzyht.notifyrelay.nativecore.device

import com.xzyht.notifyrelay.nativecore.NativeCore
import notifyrelay.base.log.Logger
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.device.service.contract.DeviceAuthTablePort

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
        deviceManager: DeviceAuthTablePort,
        target: DeviceInfo,
        header: String,
        plaintext: String,
        timeoutMs: Long = 10000L,
    ): EnqueueResult {
        val auth = deviceManager.authenticatedSnapshot()[target.uuid]
        if (auth == null || !auth.isAccepted) return EnqueueResult.AUTH_FAILED

        if (!NativeCore.senderQueueReady) {
            Logger.w(TAG, "发送队列未初始化，丢弃消息: $header -> ${target.displayName}")
            return EnqueueResult.QUEUE_UNINITIALIZED
        }

        if (!NativeCore.isReady()) return EnqueueResult.MISSING_CONTEXT
        return try {
            when (NativeCore.enqueueMessage(target.uuid, header, plaintext)) {
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
