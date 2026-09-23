package com.xzyht.notifyrelay.sync.builder

import android.content.Context
import android.net.Uri
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.nativecore.NativeCore
import com.xzyht.notifyrelay.sync.SendGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import notifyrelay.base.util.Logger
import notifyrelay.base.util.PermissionHelper
import notifyrelay.base.util.image.ImageUtils
import org.json.JSONObject

/**
 * 超级岛消息构造与发送（抽取自 [com.xzyht.notifyrelay.sync.MessageSender]）。
 *
 * 仅做搬移/抽取，逻辑与 [com.xzyht.notifyrelay.sync.MessageSender] 原实现逐行保持一致。
 * JSON 字段名（`packageName`/`appName`/`title`/`text`/`param_v2_raw`/`time`/`isLocked`/`featureIdOverride`/`pics`）
 * 与 Rust 合并引擎对齐，不可改名。
 */
object SuperIslandMessageBuilder {
    private const val TAG = "MessageSender"

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
    ): String {
        // 处理图片：若 picMap 中是本地 URI/file 路径则读取并编码为 base64 data URI，http(s) 地址或其他字符串保持不变
        val processedPics = mutableMapOf<String, String>()
        if (picMap != null) {
            withContext(Dispatchers.IO) {
                picMap.forEach { (k, v) ->
                    try {
                        val lower = v.lowercase()
                        if (lower.startsWith("content://") ||
                            lower.startsWith("file://") ||
                            v.startsWith(
                                "/",
                            )
                        ) {
                            try {
                                if (v.startsWith("/")) {
                                    val file = java.io.File(v)
                                    if (file.isFile) {
                                        val bytes = file.readBytes()
                                        processedPics[k] = ImageUtils.bytesToDataUrl(bytes, "image/png")
                                    } else {
                                        processedPics[k] = v
                                    }
                                } else {
                                    val uri = Uri.parse(v)
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        val bytes = input.readBytes()
                                        val mime =
                                            context.contentResolver.getType(uri) ?: "image/png"
                                        processedPics[k] = ImageUtils.bytesToDataUrl(bytes, mime)
                                    } ?: run {
                                        // 无法打开则回退到原始字符串
                                        processedPics[k] = v
                                    }
                                }
                            } catch (e: Exception) {
                                // 读取失败则保留原值
                                Logger.w(TAG, "读取本地图片失败: $v", e)
                                processedPics[k] = v
                            }
                        } else {
                            // 非本地资源（如 http:// 或 已经是 base64 字符串），保持原样
                            processedPics[k] = v
                        }
                    } catch (e: Exception) {
                        processedPics[k] = v
                    }
                }
            }
        }

        val finalPics: Map<String, String> = if (processedPics.isNotEmpty()) processedPics.toMap() else (picMap?.toMap() ?: emptyMap())
        val isLocked = PermissionHelper.isDeviceLocked(context)

        return JSONObject()
            .apply {
                put("packageName", superPkg)
                put("appName", appName ?: superPkg)
                put("title", title ?: "")
                put("text", text ?: "")
                put("param_v2_raw", paramV2Raw ?: "")
                put("time", time)
                put("isLocked", isLocked)
                put("featureIdOverride", featureIdOverride ?: "")
                put("pics", JSONObject(finalPics))
            }.toString()
    }

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
    ) {
        try {
            val authenticatedDevices = SendGateway.getAuthenticatedDevices(deviceManager)
            if (authenticatedDevices.isEmpty()) {
                Logger.w(TAG, "没有已认证的设备")
                return
            }

            val ctx = NativeCore.getContext() ?: return
            val queuePtr = NativeCore.senderQueuePtr
            if (queuePtr == 0L) return

            // 组装「全量」超级岛状态：差异计算（FULL/DELTA）、合并、ACK 与心跳均由 Rust 合并引擎负责。
            val content =
                buildSuperIslandFullContent(
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

            authenticatedDevices.forEach { device ->
                try {
                    NativeCore.pushSuperislandState(ctx, queuePtr, device.uuid, content, false, false)
                } catch (e: Exception) {
                    Logger.e(TAG, "超级岛: 推送超级岛状态失败: ${device.displayName}", e)
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "超级岛: 发送超级岛数据失败", e)
        }
    }

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
    ) {
        try {
            // 推送结束标记：Rust 合并引擎会回传 terminateValue="__END__" 全量，接收端据此移除该超级岛卡片。
            val ctx = NativeCore.getContext() ?: return
            val queuePtr = NativeCore.senderQueuePtr
            if (queuePtr == 0L) return

            val content =
                JSONObject()
                    .apply {
                        put("packageName", superPkg)
                        put("appName", appName ?: superPkg)
                        put("title", title ?: "")
                        put("text", text ?: "")
                        put("param_v2_raw", paramV2Raw ?: "")
                        put("time", time)
                        put("featureIdOverride", featureIdOverride ?: "")
                    }.toString()

            SendGateway.getAuthenticatedDevices(deviceManager).forEach { device ->
                try {
                    NativeCore.pushSuperislandState(ctx, queuePtr, device.uuid, content, true, false)
                } catch (e: Exception) {
                    Logger.e(TAG, "超级岛: 推送超级岛结束失败: ${device.displayName}", e)
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "超级岛: 发送终止事件失败", e)
        }
    }
}
