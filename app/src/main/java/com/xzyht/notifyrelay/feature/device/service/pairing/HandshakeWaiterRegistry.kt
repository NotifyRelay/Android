package com.xzyht.notifyrelay.feature.device.service.pairing

import kotlinx.coroutines.CompletableDeferred

/**
 * 握手结果等待器登记表。
 *
 * 发起配对/连接时由 UI 层注册一个 [CompletableDeferred]，Rust 回调侧拿到结果后完成它，
 * 使得调用方可以「挂起等待远端响应」而不需要轮询。
 *
 * 同一 uuid 只保留最新的等待器：重复注册会取消上一个（`CompletableDeferred.cancel()`），
 * 避免迟到结果误唤醒旧等待者。
 */
class HandshakeWaiterRegistry {
    private val pending = mutableMapOf<String, CompletableDeferred<Boolean>>()

    /**
     * 最近一次失败原因（key=uuid）。
     *
     * 供 UI 把笼统的"配对超时/验证失败"细化为真实原因（如 core 版本不兼容）。
     * 仅保留失败原因；成功或重新注册时清除，避免残留误导。
     */
    private val failureReasons = mutableMapOf<String, String>()

    /** 注册等待握手结果（同一 uuid 的旧等待器会被取消）。 */
    fun register(uuid: String): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        synchronized(pending) {
            pending[uuid]?.cancel()
            pending[uuid] = deferred
            failureReasons.remove(uuid)
        }
        return deferred
    }

    /**
     * 解析挂起的握手结果。
     *
     * @param reason 失败原因码（成功时忽略），如 `version_mismatch`、`rejected`。
     */
    fun resolve(
        uuid: String,
        success: Boolean,
        reason: String? = null,
    ) {
        synchronized(pending) {
            if (success) {
                failureReasons.remove(uuid)
            } else if (!reason.isNullOrBlank()) {
                failureReasons[uuid] = reason
            }
            pending.remove(uuid)?.complete(success)
        }
    }

    /** 读取该 uuid 最近一次失败原因（不清除；由 [register] 在下次注册时清理）。 */
    fun failureReason(uuid: String): String? = synchronized(pending) { failureReasons[uuid] }

    /** 按 Deferred 实例清理等待器，防止迟到请求完成或移除其他等待器。 */
    fun cancel(
        uuid: String,
        deferred: CompletableDeferred<Boolean>,
    ) {
        synchronized(pending) {
            if (pending[uuid] === deferred) {
                pending.remove(uuid)
                deferred.cancel()
            }
        }
    }
}
