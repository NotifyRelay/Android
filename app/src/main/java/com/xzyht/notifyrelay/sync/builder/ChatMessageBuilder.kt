package com.xzyht.notifyrelay.sync.builder

import android.content.Context
import com.xzyht.notifyrelay.nativecore.device.appsync.ChatSendGateway
import com.xzyht.notifyrelay.nativecore.device.notification.LocalNotificationForwardGateway
import notifyrelay.base.util.PermissionHelper
import notifyrelay.domain.feature.device.service.contract.DeviceMessagePort

/**
 * 聊天/通知消息构造与发送。
 *
 * 聊天测试的报文构造见公共端 [ChatSendGateway]；本机通知转发统一委托公共端
 * [LocalNotificationForwardGateway]，本类只补 Android 侧的 `Context` 取值（锁屏状态）。
 */
object ChatMessageBuilder {
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
     * @param context 上下文（仅用于取锁屏状态）
     * @param packageName 应用包名（Android 侧即本地包名，无需反查）
     * @param appName 应用名称
     * @param title 通知标题
     * @param text 通知内容
     * @param time 通知时间
     * @param deviceManager 设备管理器
     * @param notificationKey 通知稳定标识键（NotificationListenerService 的 sbn.key）
     */
    suspend fun sendNotificationMessage(
        context: Context,
        packageName: String,
        appName: String?,
        title: String?,
        text: String?,
        time: Long,
        deviceManager: DeviceMessagePort,
        notificationKey: String? = null,
    ) {
        LocalNotificationForwardGateway.forward(
            notification =
                LocalNotificationForwardGateway.LocalNotification(
                    id = notificationKey ?: (time.toString() + packageName),
                    appName = appName ?: packageName,
                    rawPackage = packageName,
                    title = title ?: "",
                    text = text ?: "",
                    // Android 侧已带本地包名，且入站前已由通知库落库
                    resolvePackage = false,
                    isLocked = PermissionHelper.isDeviceLocked(context),
                    time = time,
                    notificationKey = notificationKey ?: ("local_" + time + packageName),
                ),
            deviceManager = deviceManager,
            persistLocally = false,
            // 通知库已按「时间 + 包名 + 本机」落库，检定需排除本条自身
            existingKey = time.toString() + packageName + LOCAL_DEVICE,
        )
    }

    /** 通知库写入本机记录时使用的设备标识。 */
    private const val LOCAL_DEVICE = "本机"
}
