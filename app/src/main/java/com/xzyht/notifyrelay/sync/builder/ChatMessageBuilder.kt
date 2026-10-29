package com.xzyht.notifyrelay.sync.builder

import android.content.Context
import com.xzyht.notifyrelay.nativecore.device.SendGateway
import com.xzyht.notifyrelay.nativecore.device.appsync.ChatSendGateway
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import notifyrelay.base.log.Logger
import notifyrelay.base.util.PermissionHelper
import notifyrelay.domain.feature.device.service.contract.DeviceMessagePort

/**
 * 聊天/通知消息构造与发送。
 *
 * 聊天测试的报文构造已上移到公共端 [ChatSendGateway]；本类保留 Android 侧的 `Context` 入口，
 * 并承载通知转发报文（含锁屏状态）的构造。
 */
object ChatMessageBuilder {
    private const val TAG = "MessageSender"

    private val json = kotlinx.serialization.json.Json { encodeDefaults = false }

    /**
     * 发送聊天测试消息。
     *
     * @param context 上下文（仅用于取本机包名）。
     * @param message 消息内容。
     * @param deviceManager 设备管理器。
     */
    fun sendChatMessage(
        context: Context,
        message: String,
        deviceManager: DeviceMessagePort,
    ) = ChatSendGateway.sendChatMessage(context.packageName, message, deviceManager)

    /**
     * 发送普通通知转发消息。
     *
     * @param context 上下文
     * @param packageName 应用包名
     * @param appName 应用名称
     * @param title 通知标题
     * @param text 通知内容
     * @param time 通知时间
     * @param deviceManager 设备管理器
     * @param notificationKey 通知稳定标识键（NotificationListenerService 的 sbn.key），缺省时不写入 JSON
     */
    fun sendNotificationMessage(
        context: Context,
        packageName: String,
        appName: String?,
        title: String?,
        text: String?,
        time: Long,
        deviceManager: DeviceMessagePort,
        notificationKey: String? = null,
    ) {
        val authenticatedDevices = SendGateway.getAuthenticatedDevices(deviceManager)

        if (authenticatedDevices.isEmpty()) {
            Logger.w(TAG, "没有已认证的设备")
            return
        }

        // 获取锁屏状态
        val isLocked = PermissionHelper.isDeviceLocked(context)

        // JSON 构造
        val raw =
            buildJsonObject {
                put("packageName", packageName)
                put("appName", appName ?: packageName)
                put("title", title ?: "")
                put("text", text ?: "")
                put("time", JsonPrimitive(time))
                put("isLocked", JsonPrimitive(isLocked))
                if (!notificationKey.isNullOrEmpty()) {
                    put("notificationKey", notificationKey)
                }
            }.let { json.encodeToString(JsonObject.serializer(), it) }

        // 入队
        authenticatedDevices.forEach { deviceInfo ->
            if (!SendGateway.enqueueNotification(deviceInfo, raw, deviceManager, "通知")) {
                Logger.w(TAG, "通知入队失败: " + deviceInfo.displayName + ", pkg=" + packageName)
            }
        }

        Logger.i(TAG, "通知已加入队列，共 " + authenticatedDevices.size + " 个设备")
    }
}
