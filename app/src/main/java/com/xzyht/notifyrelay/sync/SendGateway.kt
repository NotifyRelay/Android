package com.xzyht.notifyrelay.sync

import com.xzyht.notifyrelay.feature.device.model.DeviceInfo
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.nativecore.NativeCore
import notifyrelay.base.util.Logger

/**
 * 发送网关（抽取自 [MessageSender]）。
 *
 * 仅做搬移/抽取，逻辑与 [MessageSender] 原实现逐行保持一致：
 * - [getAuthenticatedDevices]：经 [DeviceConnectionManager] 的 `internal` 访问器读取已认证设备（已去除反射）。
 * - [enqueueNotification]：通知类入队（经 Rust 发送队列：加密、限流、重试、去重由 Rust 统一处理）。
 */
internal object SendGateway {
    private const val TAG = "MessageSender"

    /**
     * 获取已认证的设备列表（不含本机）。
     *
     * 直接使用 [DeviceConnectionManager] 的 `internal` 访问器（`authenticatedDevices` / `uuid` /
     * `lookupDevice`），不再反射私有字段与方法。
     * uuid 快照在同一把锁下取出（与 `DeviceQuery` / `PairingCallbackHandler` 的既有约定一致），
     * 避免遍历期间被其它线程增删导致 ConcurrentModificationException。
     */
    fun getAuthenticatedDevices(deviceManager: DeviceConnectionManager): List<DeviceInfo> =
        try {
            val authedUuids =
                synchronized(deviceManager.authenticatedDevices) {
                    deviceManager.authenticatedDevices.keys.toList()
                }

            val authenticatedDevices = mutableListOf<DeviceInfo>()
            for (uuid in authedUuids) {
                if (uuid == deviceManager.uuid) continue
                deviceManager.lookupDevice(uuid)?.let { authenticatedDevices.add(it) }
            }

            authenticatedDevices
        } catch (e: Exception) {
            Logger.e(TAG, "获取已认证设备列表失败", e)
            emptyList()
        }

    /**
     * 将通知 JSON 入队发送（经 Rust 发送队列：加密、限流、重试、去重由 Rust 统一处理）
     */
    fun enqueueNotification(
        deviceInfo: DeviceInfo,
        json: String,
        deviceManager: DeviceConnectionManager,
        tag: String = "",
    ): Boolean {
        val ctx = NativeCore.getContext()
        if (ctx == null) {
            Logger.w(TAG, "Rust 未初始化，跳过入队: ${deviceInfo.displayName}")
            return false
        }
        val dedupKey = NativeCore.computeDedupKey(deviceInfo.uuid, json) ?: return false
        val result =
            ProtocolSender.sendEncrypted(
                deviceManager,
                deviceInfo,
                "DATA_NOTIFICATION",
                json,
                dedupKey = dedupKey,
            )
        if (result != ProtocolSender.EnqueueResult.SUCCESS) {
            Logger.w(TAG, "加入${tag}发送队列失败: ${deviceInfo.displayName}, result=$result")
            return false
        }
        return true
    }
}
