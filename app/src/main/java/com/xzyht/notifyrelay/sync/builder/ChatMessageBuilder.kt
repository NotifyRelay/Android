package com.xzyht.notifyrelay.sync.builder

import android.content.Context
import com.xzyht.notifyrelay.feature.device.model.DeviceInfo
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.notification.data.ChatMemory
import com.xzyht.notifyrelay.sync.SendGateway
import notifyrelay.base.util.Logger
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
        deviceManager: DeviceConnectionManager,
    ) {
        try {
            // 获取所有已认证设备
            val allDevices =
                deviceManager.devices.value.values
                    .map { it.first }
            val sentAny = allDevices.isNotEmpty() && message.isNotBlank()

            if (!sentAny) {
                Logger.w(TAG, "没有可用的设备或消息为空")
                return
            }

            // 构建标准 JSON 格式的消息
            val pkgName: String = context.packageName
            val raw =
                JSONObject()
                    .apply {
                        put("packageName", pkgName)
                        put("appName", "NotifyRelay")
                        put("title", "聊天测试")
                        put("text", message)
                        put("time", System.currentTimeMillis())
                    }.toString()
            allDevices.forEach { device: DeviceInfo ->
                SendGateway.enqueueNotification(device, raw, deviceManager, "聊天")
            }

            // 记录到聊天历史
            ChatMemory.append(context, "发送: $message")

            Logger.i(TAG, "聊天消息已加入队列，共发送到 ${allDevices.size} 个设备")
        } catch (e: Exception) {
            Logger.e(TAG, "发送聊天消息失败", e)
        }
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
     */
    fun sendNotificationMessage(
        context: Context,
        packageName: String,
        appName: String?,
        title: String?,
        text: String?,
        time: Long,
        deviceManager: DeviceConnectionManager,
    ) {
        try {
            val authenticatedDevices = SendGateway.getAuthenticatedDevices(deviceManager)

            if (authenticatedDevices.isEmpty()) {
                Logger.w(TAG, "没有已认证的设备")
                return
            }

            // 获取锁屏状态
            val isLocked =
                notifyrelay.base.util.PermissionHelper
                    .isDeviceLocked(context)

            // 构建标准 JSON 格式的通知数据
            val raw =
                JSONObject()
                    .apply {
                        put("packageName", packageName)
                        put("appName", appName ?: packageName)
                        put("title", title ?: "")
                        put("text", text ?: "")
                        put("time", time)
                        put("isLocked", isLocked)
                    }.toString()
            authenticatedDevices.forEach { deviceInfo ->
                SendGateway.enqueueNotification(deviceInfo, raw, deviceManager, "通知")
            }

            Logger.i(TAG, "通知已加入队列，共 ${authenticatedDevices.size} 个设备")
        } catch (e: Exception) {
            Logger.e(TAG, "发送通知消息失败", e)
        }
    }
}
