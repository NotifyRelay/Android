package com.xzyht.notifyrelay.sync.builder

import android.content.Context
import com.xzyht.notifyrelay.feature.device.model.DeviceInfo
import com.xzyht.notifyrelay.feature.device.service.contract.DeviceMessagePort
import com.xzyht.notifyrelay.feature.notification.data.ChatMemory
import com.xzyht.notifyrelay.sync.SendGateway
import notifyrelay.shared.log.Logger
import org.json.JSONException
import org.json.JSONObject

/**
 * 聊天/通知消息构造与发送（抽取自 [com.xzyht.notifyrelay.sync.MessageSender]）。
 *
 * 仅做搬移/抽取，逻辑与 [com.xzyht.notifyrelay.sync.MessageSender] 原实现逐行保持一致。
 */
object ChatMessageBuilder {
    private const val TAG = "MessageSender"

    /**
     * 发送聊天测试消息
     * @param context 上下文
     * @param message 消息内容
     * @param deviceManager 设备管理器
     */
    fun sendChatMessage(
        context: Context,
        message: String,
        deviceManager: DeviceMessagePort,
    ) {
        // 设备查找
        val allDevices =
            deviceManager.devices.value.values
                .map { it.first }
        val sentAny = allDevices.isNotEmpty() && message.isNotBlank()

        if (!sentAny) {
            Logger.w(TAG, "没有可用的设备或消息为空")
            return
        }

        // JSON 构造
        val pkgName: String = context.packageName
        val raw =
            try {
                JSONObject()
                    .apply {
                        put("packageName", pkgName)
                        put("appName", "NotifyRelay")
                        put("title", "聊天测试")
                        put("text", message)
                        put("time", System.currentTimeMillis())
                    }.toString()
            } catch (e: JSONException) {
                Logger.e(TAG, "构造聊天消息 JSON 失败: pkg=$pkgName", e)
                return
            }

        // 入队
        allDevices.forEach { device: DeviceInfo ->
            if (!SendGateway.enqueueNotification(device, raw, deviceManager, "聊天")) {
                Logger.w(TAG, "聊天消息入队失败: ${device.displayName}")
            }
        }

        // 记录到聊天历史
        ChatMemory.append(context, "发送: $message")

        Logger.i(TAG, "聊天消息已加入队列，共发送到 ${allDevices.size} 个设备")
    }

    /**
     * 发送普通通知转发消息
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
        // 设备查找
        val authenticatedDevices = SendGateway.getAuthenticatedDevices(deviceManager)

        if (authenticatedDevices.isEmpty()) {
            Logger.w(TAG, "没有已认证的设备")
            return
        }

        // 获取锁屏状态
        val isLocked =
            notifyrelay.base.util.PermissionHelper
                .isDeviceLocked(context)

        // JSON 构造
        val raw =
            try {
                JSONObject()
                    .apply {
                        put("packageName", packageName)
                        put("appName", appName ?: packageName)
                        put("title", title ?: "")
                        put("text", text ?: "")
                        put("time", time)
                        put("isLocked", isLocked)
                        if (!notificationKey.isNullOrEmpty()) {
                            put("notificationKey", notificationKey)
                        }
                    }.toString()
            } catch (e: JSONException) {
                Logger.e(TAG, "构造通知消息 JSON 失败: pkg=$packageName, notificationKey=$notificationKey", e)
                return
            }

        // 入队
        authenticatedDevices.forEach { deviceInfo ->
            if (!SendGateway.enqueueNotification(deviceInfo, raw, deviceManager, "通知")) {
                Logger.w(TAG, "通知入队失败: ${deviceInfo.displayName}, pkg=$packageName")
            }
        }

        Logger.i(TAG, "通知已加入队列，共 ${authenticatedDevices.size} 个设备")
    }
}
