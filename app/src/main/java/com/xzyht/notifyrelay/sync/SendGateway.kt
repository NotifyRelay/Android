package com.xzyht.notifyrelay.sync

import com.xzyht.notifyrelay.feature.device.model.DeviceInfo
import com.xzyht.notifyrelay.feature.device.service.contract.DeviceAuthTablePort
import com.xzyht.notifyrelay.feature.device.service.contract.DeviceQueryPort
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
     * 经 [DeviceAuthTablePort] 的只读快照读取已认证 uuid，再逐个解析设备信息；
     * 快照在实现方同一把锁内复制，遍历期间不会被其它线程增删。
     */
    fun getAuthenticatedDevices(deviceManager: DeviceAuthTablePort): List<DeviceInfo> =
        try {
            val snapshot = deviceManager.authenticatedSnapshot()
            val localUuid = deviceManager.localUuid
            val query = deviceManager as? DeviceQueryPort

            val authenticatedDevices = mutableListOf<DeviceInfo>()
            for (uuid in snapshot.keys) {
                if (uuid == localUuid) continue
                query?.resolveDeviceInfo(uuid, null, 23333)?.let { authenticatedDevices.add(it) }
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
        deviceManager: DeviceAuthTablePort,
        tag: String = "",
    ): Boolean {
        val ctx = NativeCore.getContext()
        if (ctx == null) {
            Logger.w(TAG, "Rust 未初始化，跳过入队: ${deviceInfo.displayName}")
            return false
        }
        val result =
            ProtocolSender.sendEncrypted(
                deviceManager,
                deviceInfo,
                "DATA_NOTIFICATION",
                json,
            )
        if (result == ProtocolSender.EnqueueResult.DEDUPED) {
            Logger.d(TAG, "命中去重丢弃: ${deviceInfo.displayName}")
            return true
        }
        if (result != ProtocolSender.EnqueueResult.SUCCESS) {
            Logger.w(TAG, "加入${tag}发送队列失败: ${deviceInfo.displayName}, result=$result")
            return false
        }
        return true
    }
}
