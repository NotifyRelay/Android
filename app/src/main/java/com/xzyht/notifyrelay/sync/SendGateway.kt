package com.xzyht.notifyrelay.sync

import com.xzyht.notifyrelay.feature.device.model.DeviceInfo
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.nativecore.NativeCore
import notifyrelay.base.util.Logger

/**
 * 发送网关（抽取自 [MessageSender]）。
 *
 * 仅做搬移/抽取，逻辑与 [MessageSender] 原实现逐行保持一致：
 * - [getAuthenticatedDevices]：反射取 [DeviceConnectionManager] 私有字段/方法（见 plan 步骤3，本分支保留反射）。
 * - [enqueueNotification]：通知类入队（经 Rust 发送队列：加密、限流、重试、去重由 Rust 统一处理）。
 */
internal object SendGateway {
    private const val TAG = "MessageSender"

    /**
     * 获取已认证的设备列表
     * @param deviceManager 设备管理器
     * @return 已认证设备的列表
     */
    fun getAuthenticatedDevices(deviceManager: DeviceConnectionManager): List<DeviceInfo> {
        return try {
            val field = deviceManager::class.java.getDeclaredField("authenticatedDevices")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val authedMap = field.get(deviceManager) as? Map<String, *>

            val myUuidField = deviceManager::class.java.getDeclaredField("uuid")
            myUuidField.isAccessible = true
            val myUuid = myUuidField.get(deviceManager) as? String

            val authenticatedDevices = mutableListOf<DeviceInfo>()

            authedMap?.forEach { (uuid, _) ->
                if (uuid == myUuid) return@forEach

                val infoMethod = deviceManager::class.java.getDeclaredMethod("getDeviceInfo", String::class.java)
                infoMethod.isAccessible = true
                val deviceInfo = infoMethod.invoke(deviceManager, uuid) as? DeviceInfo

                if (deviceInfo != null) {
                    authenticatedDevices.add(deviceInfo)
                }
            }

            authenticatedDevices
        } catch (e: Exception) {
            Logger.e(TAG, "获取已认证设备列表失败", e)
            emptyList()
        }
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
