package com.xzyht.notifyrelay.sync.builder

import android.content.Context
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.nativecore.NativeCore
import com.xzyht.notifyrelay.sync.SendGateway
import notifyrelay.base.util.Logger
import notifyrelay.base.util.PermissionHelper
import org.json.JSONObject

/**
 * 媒体消息构造与发送（抽取自 [com.xzyht.notifyrelay.sync.MessageSender]）。
 *
 * 仅做搬移/抽取，逻辑与 [com.xzyht.notifyrelay.sync.MessageSender] 原实现逐行保持一致。
 * JSON 字段名（`packageName`/`appName`/`title`/`text`/`time`/`isLocked`/`coverUrl`/`isPlaying`）
 * 与 Rust 合并引擎对齐，不可改名。
 */
object MediaMessageBuilder {
    private const val TAG = "MessageSender"

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
    ): String {
        val isLocked = PermissionHelper.isDeviceLocked(context)
        return JSONObject()
            .apply {
                put("packageName", packageName)
                put("appName", appName ?: packageName)
                put("title", title ?: "")
                put("text", text ?: "")
                put("coverUrl", coverUrl ?: "")
                put("time", time)
                put("isLocked", isLocked)
                put("isPlaying", isPlaying)
            }.toString()
    }

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
    ) {
        try {
            // 推送「全量」媒体状态：差异计算（FULL/DELTA）、合并与 ACK 均由 Rust 合并引擎负责。
            val ctx = NativeCore.getContext() ?: return
            val queuePtr = NativeCore.senderQueuePtr
            if (queuePtr == 0L) return

            val content =
                buildMediaFullContent(
                    context,
                    packageName,
                    appName,
                    title,
                    text,
                    coverUrl,
                    time,
                    isPlaying = true,
                )

            SendGateway.getAuthenticatedDevices(deviceManager).forEach { device ->
                try {
                    NativeCore.pushMediaState(ctx, queuePtr, device.uuid, content, false, false)
                } catch (e: Exception) {
                    Logger.w(TAG, "推送媒体状态失败: ${device.displayName}", e)
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "发送媒体播放通知失败", e)
        }
    }

    /**
     * 发送媒体播放结束通知
     */
    fun sendMediaPlayEndNotification(
        context: Context,
        packageName: String,
        appName: String?,
        time: Long,
        deviceManager: DeviceConnectionManager,
    ) {
        try {
            // 推送结束标记：Rust 合并引擎会回传 terminateValue="__END__" 全量，接收端据此移除媒体卡片。
            val ctx = NativeCore.getContext() ?: return
            val queuePtr = NativeCore.senderQueuePtr
            if (queuePtr == 0L) return

            SendGateway.getAuthenticatedDevices(deviceManager).forEach { device ->
                try {
                    NativeCore.pushMediaState(ctx, queuePtr, device.uuid, "{}", true, false)
                } catch (e: Exception) {
                    Logger.w(TAG, "推送媒体结束失败: ${device.displayName}", e)
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "发送媒体播放结束通知失败", e)
        }
    }
}
