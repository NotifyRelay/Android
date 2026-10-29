package com.xzyht.notifyrelay.nativecore.device.appsync

import com.xzyht.notifyrelay.nativecore.device.SendGateway
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import notifyrelay.base.log.Logger
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.device.service.contract.DeviceMessagePort
import notifyrelay.domain.feature.notification.chat.ChatMemory

/**
 * 聊天测试报文的构造与发送。
 *
 * 报文五字段（`packageName` / `appName` / `title` / `text` / `time`）与字段顺序为对端解析契约，
 * 不可改动；发送经 [SendGateway] 统一入队（加密、限流、重试、去重由 Rust 承担）。
 */
object ChatSendGateway {
    private const val TAG = "MessageSender"

    /** 报文中的应用显示名。 */
    private const val APP_NAME = "NotifyRelay"

    /** 报文中的通知标题。 */
    private const val TITLE = "聊天测试"

    private val json = Json { encodeDefaults = false }

    /**
     * 发送聊天测试消息并记录到聊天记忆。
     *
     * @param localPackageName 本机应用包名（对应报文 `packageName` 字段）。
     * @param message 消息内容。
     * @param deviceManager 设备消息端口。
     */
    fun sendChatMessage(
        localPackageName: String,
        message: String,
        deviceManager: DeviceMessagePort,
    ) {
        val allDevices =
            deviceManager.devices.value.values
                .map { it.first }
        if (allDevices.isEmpty() || message.isBlank()) {
            Logger.w(TAG, "没有可用的设备或消息为空")
            return
        }

        val raw =
            json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("packageName", localPackageName)
                    put("appName", APP_NAME)
                    put("title", TITLE)
                    put("text", message)
                    put("time", JsonPrimitive(System.currentTimeMillis()))
                },
            )

        allDevices.forEach { device: DeviceInfo ->
            if (!SendGateway.enqueueNotification(device, raw, deviceManager, "聊天")) {
                Logger.w(TAG, "聊天消息入队失败: " + device.displayName)
            }
        }

        ChatMemory.append("发送: " + message)
        Logger.i(TAG, "聊天消息已加入队列，共发送到 " + allDevices.size + " 个设备")
    }

    /** 是否有可用设备。 */
    fun hasAvailableDevices(deviceManager: DeviceMessagePort): Boolean = deviceManager.devices.value.isNotEmpty()

    /** 消息内容是否有效。 */
    fun isValidMessage(message: String?): Boolean = !message.isNullOrBlank()
}
