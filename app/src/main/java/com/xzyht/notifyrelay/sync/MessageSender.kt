package com.xzyht.notifyrelay.sync

import android.content.Context
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.notification.HighPriorityNotifier
import com.xzyht.notifyrelay.sync.builder.ChatMessageBuilder
import com.xzyht.notifyrelay.sync.builder.MediaMessageBuilder
import com.xzyht.notifyrelay.sync.builder.SuperIslandMessageBuilder

/**
 * 消息发送门面（整合聊天测试、普通通知转发、媒体与超级岛数据、本地高优先级通知）。
 *
 * 发送队列、限流、重试与去重均由 Rust core 发送队列统一处理。
 * 本文件仅作转发门面，保持外部 API 不变；实际实现已拆分至 [SendGateway] 与各 `builder`。
 */
object MessageSender {
    /**
     * 组装「全量」媒体状态（与 Rust 合并引擎对齐，供主动推送与查询回调共用）。
     */
    fun buildMediaFullContent(
        context: Context,
        packageName: String,
        appName: String?,
        title: String?,
        text: String?,
        coverUrl: String?,
        time: Long,
        isPlaying: Boolean = true,
    ): String =
        MediaMessageBuilder.buildMediaFullContent(
            context,
            packageName,
            appName,
            title,
            text,
            coverUrl,
            time,
            isPlaying,
        )

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
    ) = ChatMessageBuilder.sendChatMessage(context, message, deviceManager)

    /**
     * 发送媒体播放通知
     * 使用专门的协议前缀标记媒体通知，支持状态变化跟踪
     * 差异计算（FULL/DELTA）、合并、ACK 与心跳均由 Rust 合并引擎负责。
     */
    fun sendMediaPlayNotification(
        context: Context,
        packageName: String,
        appName: String?,
        title: String?,
        text: String?,
        coverUrl: String?,
        time: Long,
        deviceManager: DeviceConnectionManager,
    ) = MediaMessageBuilder.sendMediaPlayNotification(
        context,
        packageName,
        appName,
        title,
        text,
        coverUrl,
        time,
        deviceManager,
    )

    /**
     * 发送媒体播放结束通知
     */
    fun sendMediaPlayEndNotification(
        context: Context,
        packageName: String,
        appName: String?,
        time: Long,
        deviceManager: DeviceConnectionManager,
    ) = MediaMessageBuilder.sendMediaPlayEndNotification(
        context,
        packageName,
        appName,
        time,
        deviceManager,
    )

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
    ) = ChatMessageBuilder.sendNotificationMessage(
        context,
        packageName,
        appName,
        title,
        text,
        time,
        deviceManager,
    )

    /**
     * 组装「全量」超级岛状态（与 Rust 合并引擎对齐，供主动推送与查询回调共用）。
     * 图片处理：本地 URI/file 路径读取并编码为 base64 data URI，http(s) 地址或其他字符串保持不变。
     */
    suspend fun buildSuperIslandFullContent(
        context: Context,
        superPkg: String,
        appName: String?,
        title: String?,
        text: String?,
        time: Long,
        paramV2Raw: String?,
        picMap: Map<String, String>?,
        featureIdOverride: String?,
    ): String =
        SuperIslandMessageBuilder.buildSuperIslandFullContent(
            context,
            superPkg,
            appName,
            title,
            text,
            time,
            paramV2Raw,
            picMap,
            featureIdOverride,
        )

    /**
     * 发送超级岛专用数据（包含 param_v2 原始 JSON 与图片 map）
     */
    suspend fun sendSuperIslandData(
        context: Context,
        superPkg: String,
        appName: String?,
        title: String?,
        text: String?,
        time: Long,
        paramV2Raw: String?,
        picMap: Map<String, String>?,
        deviceManager: DeviceConnectionManager,
        featureIdOverride: String? = null,
    ) = SuperIslandMessageBuilder.sendSuperIslandData(
        context,
        superPkg,
        appName,
        title,
        text,
        time,
        paramV2Raw,
        picMap,
        deviceManager,
        featureIdOverride,
    )

    /**
     * 发送超级岛终止事件：当本地确认没有该超级岛通知时调用。
     */
    fun sendSuperIslandEnd(
        context: Context,
        superPkg: String,
        appName: String?,
        time: Long,
        paramV2Raw: String?,
        title: String?,
        text: String?,
        deviceManager: DeviceConnectionManager,
        featureIdOverride: String? = null,
    ) = SuperIslandMessageBuilder.sendSuperIslandEnd(
        context,
        superPkg,
        appName,
        time,
        paramV2Raw,
        title,
        text,
        deviceManager,
        featureIdOverride,
    )

    /**
     * 发送高优先级悬浮通知（用于应用跳转指示）。
     * 实现已抽取至 [com.xzyht.notifyrelay.feature.notification.HighPriorityNotifier]，此处仅转发。
     * @param context 上下文
     * @param title 通知标题
     * @param text 通知内容
     */
    fun sendHighPriorityNotification(
        context: Context,
        title: String?,
        text: String?,
    ) = HighPriorityNotifier.send(context, title, text)

    /**
     * 检查是否有可用的设备
     * @param deviceManager 设备管理器
     * @return 是否有可用的设备
     */
    fun hasAvailableDevices(deviceManager: DeviceConnectionManager): Boolean = deviceManager.devices.value.isNotEmpty()

    /**
     * 检查消息是否有效
     * @param message 消息内容
     * @return 消息是否有效
     */
    fun isValidMessage(message: String?): Boolean = !message.isNullOrBlank()
}
