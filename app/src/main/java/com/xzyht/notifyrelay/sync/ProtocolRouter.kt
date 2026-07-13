package com.xzyht.notifyrelay.sync

import android.content.Context
import android.os.Build
import android.os.Environment
import com.xzyht.notifyrelay.feature.device.service.AuthInfo
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.notification.superisland.RemoteMediaSessionManager
import com.xzyht.notifyrelay.servers.MediaControlUtil
import com.xzyht.notifyrelay.servers.clipboard.ClipboardProcessor
import com.xzyht.notifyrelay.sync.ftpServer.StartResult.ALREADY_RUNNING
import com.xzyht.notifyrelay.sync.ftpServer.StartResult.CONFIG_ERROR
import com.xzyht.notifyrelay.sync.ftpServer.StartResult.FAILED
import com.xzyht.notifyrelay.sync.ftpServer.StartResult.PERMISSION_DENIED
import com.xzyht.notifyrelay.sync.ftpServer.StartResult.PORT_IN_USE
import com.xzyht.notifyrelay.sync.ftpServer.StartResult.SUCCESS
import com.xzyht.notifyrelay.sync.notification.NotificationProcessor
import com.xzyht.notifyrelay.sync.notification.StatusProcessor
import com.xzyht.notifyrelay.sync.notification.SuperIslandProcessor
import com.xzyht.notifyrelay.ui.activity.GuideActivity
import kotlinx.coroutines.launch
import notifyrelay.base.util.IntentUtils
import notifyrelay.base.util.Logger
import org.json.JSONObject

/**
 * 统一协议路由器
 *
 * 职责：
 * - 统一解析 TCP 文本首行中的 DATA_* 报文头
 * - 统一做认证检查与解密
 * - 将明文负载转发给对应的功能模块（通知/图标/应用列表等）
 *
 * 不处理：HANDSHAKE、HEARTBEAT、以及“手动发现”之类的特殊报文（仍由 DeviceConnectionManager 内部处理）。
 */
object ProtocolRouter {
    private const val TAG = "ProtocolRouter"
    private const val DEVICE_TYPE_PC = "pc"

    private fun isRemoteDevicePc(auth: AuthInfo?): Boolean {
        // 只允许明确为PC设备的情况
        // 设备类型应该从握手消息中正确获取并设置
        return auth?.deviceType?.lowercase() == DEVICE_TYPE_PC
    }

    /**
     * 处理已由 Rust 解密后的 DATA* 消息。
     * @return true 表示已处理并应由上层关闭当前连接；false 表示非本路由器负责。
     */
    fun handleDecryptedData(
        header: String,
        localUuid: String,
        plaintext: String,
        clientIp: String,
        deviceManager: DeviceConnectionManager,
        context: Context
    ): Boolean {
        val auth = synchronized(deviceManager.authenticatedDevices) { deviceManager.authenticatedDevices[localUuid] }
        if (auth == null || !auth.isAccepted) {
            Logger.d(TAG, "未认证或未接受的设备，丢弃: uuid=$localUuid, header=$header")
            return true
        }
        return routeDecrypted(header, localUuid, plaintext, clientIp, deviceManager, context, auth)
    }

    private fun routeDecrypted(
        header: String,
        remoteUuid: String,
        decrypted: String,
        clientIp: String,
        deviceManager: DeviceConnectionManager,
        context: Context,
        auth: AuthInfo?
    ): Boolean {
        return try {
            when (header) {
                // 主通道：历史上的 DATA 默认为普通通知（DATA_NOTIFICATION）
                "DATA", "DATA_NOTIFICATION" -> {
                    Logger.d(TAG, "接收到 DATA_NOTIFICATION 消息: uuid=$remoteUuid, size=${decrypted.length}")
                    val routedHeader = "DATA_NOTIFICATION"
                    NotificationProcessor.process(
                        context,
                        deviceManager,
                        deviceManager.coroutineScopeInternal,
                        NotificationProcessor.NotificationInput(
                            header = routedHeader,
                            rawData = decrypted,
                            remoteUuid = remoteUuid
                        ),
                        deviceManager.notificationDataReceivedCallbacksInternal
                    )
                    true
                }
                "DATA_SUPERISLAND" -> {
                    // 分流到 SuperIslandProcessor 专门处理超级岛通知
                    Logger.d(TAG, "接收到 DATA_SUPERISLAND 消息: uuid=$remoteUuid, size=${decrypted.length}")
                    try {
                        val handled = SuperIslandProcessor.process(
                            context,
                            deviceManager,
                            decrypted,
                            remoteUuid
                        )
                        if (handled) return true
                    } catch (e: Exception) {
                        Logger.e(TAG, "SuperIsland 处理异常", e)
                    }
                    true
                }
                "DATA_MEDIAPLAY" -> {
                    // 处理远端媒体播放通知，触发超级岛显示
                    Logger.d(TAG, "接收到 DATA_MEDIAPLAY 消息: uuid=$remoteUuid, size=${decrypted.length}")
                    try {
                        val json = JSONObject(decrypted)
                        val source = deviceManager.resolveDeviceInfo(remoteUuid, clientIp, 23333)
                        Logger.i(TAG, "收到远端媒体播放DATA_MEDIAPLAY: ${json.optString("title", "")} - ${json.optString("text", "")} (来自 ${source?.displayName ?: "未知设备"})")
                        source?.let { RemoteMediaSessionManager.onMediaMessageReceived(context, json, it) }
                    } catch (e: Exception) {
                        Logger.e(TAG, "处理远端媒体播放通知DATA_MEDIAPLAY", e)
                    }
                    true
                }
                // DATA_ICON_REQUEST：对方向本机请求应用图标，本机查找后会通过 DATA_ICON_RESPONSE 回传
                "DATA_ICON_REQUEST" -> {
                    Logger.d(TAG, "接收到 DATA_ICON_REQUEST 消息: uuid=$remoteUuid, size=${decrypted.length} 丢给IconSyncManager")
                    val source = deviceManager.resolveDeviceInfo(remoteUuid, clientIp, 23333)
                    Logger.d(TAG, "source device info: $source")
                    try {
                        source?.let { IconSyncManager.handleIconRequest(decrypted, deviceManager, it, context) }
                    } catch (e: Exception) {
                        Logger.e(TAG, "调用 IconSyncManager.handleIconRequest 异常", e)
                    }
                    true
                }
                // DATA_ICON_RESPONSE：图标请求的响应，更新本机图标缓存供通知复刻使用
                "DATA_ICON_RESPONSE" -> {
                    Logger.d(TAG, "接收到 DATA_ICON_RESPONSE 消息: uuid=$remoteUuid, size=${decrypted.length}")
                    IconSyncManager.handleIconResponse(decrypted, context)
                    true
                }
                // DATA_APP_LIST_REQUEST：对方请求本机应用列表，本机查询后通过 DATA_APP_LIST_RESPONSE 返回
                "DATA_APP_LIST_REQUEST" -> {
                    Logger.d(TAG, "接收到 DATA_APP_LIST_REQUEST 消息: uuid=$remoteUuid, size=${decrypted.length} 丢给AppListSyncManager")
                    val source = deviceManager.resolveDeviceInfo(remoteUuid, clientIp, 23333)
                    Logger.d(TAG, "source device info: $source")
                    try {
                        source?.let { AppListSyncManager.handleAppListRequest(decrypted, deviceManager, it, context) }
                    } catch (e: Exception) {
                        Logger.e(TAG, "调用 AppListSyncManager.handleAppListRequest 异常", e)
                    }
                    true
                }
                // DATA_APP_LIST_RESPONSE：应用列表请求的响应，用于更新本机缓存/状态
                "DATA_APP_LIST_RESPONSE" -> {
                    Logger.d(TAG, "接收到 DATA_APP_LIST_RESPONSE 消息: uuid=$remoteUuid, size=${decrypted.length}")
                    AppListSyncManager.handleAppListResponse(decrypted, context, remoteUuid, deviceManager)
                    true
                }
                // DATA_AUDIO_REQUEST：对方请求本机音频转发
                "DATA_MEDIA_CONTROL" -> {
                    Logger.d(TAG, "接收到 DATA_MEDIA_CONTROL 消息: uuid=$remoteUuid, size=${decrypted.length}")
                    // 处理媒体控制命令，包括音频转发和媒体播放控制
                    try {
                        val json = JSONObject(decrypted)
                        val action = json.getString("action")
                        
                        // 执行相应的媒体控制操作，优先通过通知的 PendingIntent 触发
                        when (action) {
                            // 媒体播放控制
                            "playPause" -> {
                                try {
                                    MediaControlUtil.playPause()
                                    Logger.i(TAG, "执行 playPause 成功")
                                    // 发送响应
                                    sendMediaControlResponse(deviceManager, remoteUuid, clientIp, "playPause", "success", null)
                                } catch (e: Exception) {
                                    Logger.e(TAG, "执行 playPause 失败", e)
                                    // 发送错误响应
                                    sendMediaControlResponse(deviceManager, remoteUuid, clientIp, "playPause", "error", e.message)
                                }
                            }
                            "next" -> {
                                try {
                                    MediaControlUtil.next()
                                    Logger.i(TAG, "执行 next 成功")
                                    // 发送响应
                                    sendMediaControlResponse(deviceManager, remoteUuid, clientIp, "next", "success", null)
                                } catch (e: Exception) {
                                    Logger.e(TAG, "执行 next 失败", e)
                                    // 发送错误响应
                                    sendMediaControlResponse(deviceManager, remoteUuid, clientIp, "next", "error", e.message)
                                }
                            }
                            "previous" -> {
                                try {
                                    MediaControlUtil.previous()
                                    Logger.i(TAG, "执行 previous 成功")
                                    // 发送响应
                                    sendMediaControlResponse(deviceManager, remoteUuid, clientIp, "previous", "success", null)
                                } catch (e: Exception) {
                                    Logger.e(TAG, "执行 previous 失败", e)
                                    // 发送错误响应
                                    sendMediaControlResponse(deviceManager, remoteUuid, clientIp, "previous", "error", e.message)
                                }
                            }
                            "audioRequest" -> {
                                try {
                                    val sourceDevice = deviceManager.resolveDeviceInfo(remoteUuid, clientIp, 23333)
                                    val adbPort = notifyrelay.data.config.ScrcpyDefaults.ADB_PORT
                                    
                                    val success = sourceDevice?.let {
                                        io.github.miuzarte.scrcpyforandroid.services.AudioForwardingService.startAudioForwarding(
                                            context,
                                            it.ip,
                                            adbPort,
                                            sourceDevice.displayName
                                        )
                                    }
                                    
                                    val result = if (success == true) "accepted" else "rejected"
                                    val response = "{\"type\":\"MEDIA_CONTROL\",\"action\":\"audioResponse\",\"result\":\"$result\"}"
                                    sourceDevice?.let { ProtocolSender.sendEncrypted(deviceManager, it, "DATA_MEDIA_CONTROL", response) }
                                    
                                    if (success == true) {
                                        Logger.i(TAG, "音频转发已启动: ${sourceDevice.displayName}")
                                    } else {
                                        sourceDevice?.let { Logger.e(TAG, "音频转发启动失败: ${it.displayName}") }
                                    }
                                } catch (e: Exception) {
                                    Logger.e(TAG, "处理音频转发请求失败", e)
                                    val response = "{\"type\":\"MEDIA_CONTROL\",\"action\":\"audioResponse\",\"result\":\"rejected\"}"
                                    deviceManager.resolveDeviceInfo(remoteUuid, clientIp, 23333)?.let { ProtocolSender.sendEncrypted(deviceManager, it, "DATA_MEDIA_CONTROL", response) }
                                }
                            }
                            "audioResponse" -> {
                                try {
                                    val json = JSONObject(decrypted)
                                    val result = json.optString("result", "rejected")
                                    
                                    if (result == "accepted") {
                                        Logger.i(TAG, "音频转发请求已被接受")
                                    } else {
                                        Logger.w(TAG, "音频转发请求被拒绝")
                                        deviceManager.coroutineScopeInternal.launch {
                                            notifyrelay.base.util.ToastUtils.showShortToast(context, "音频转发请求被拒绝")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Logger.e(TAG, "处理 audioResponse 失败", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "处理媒体控制命令失败", e)
                    }
                    true
                }
                "DATA_FTP" -> {
                    Logger.d(TAG, "接收到 DATA_FTP 消息，clientIp: $clientIp, remoteUuid: $remoteUuid")
                    if (!isRemoteDevicePc(auth)) {
                        Logger.w(TAG, "FTP 请求被忽略：非 PC 设备")
                        return true
                    }
                    Logger.d(TAG, "设备类型验证通过，开始处理 ftp 命令")
                    // 使用设备管理器的协程作用域处理 suspend 函数调用
                    deviceManager.coroutineScopeInternal.launch {
                        try {
                            val json = JSONObject(decrypted)
                            val action = json.optString("action", "")
                            Logger.i(TAG, "ftp 命令 action: $action")

                            when (action) {
                                "start" -> {
                                    Logger.i(TAG, "开始启动 FTP 服务器")
                                    val deviceName = deviceManager.getLocalDisplayName()
                                    val pcUsername = json.optString("username", null)
                                    val pcPassword = json.optString("password", null)
                                    val ftpStartResult = ftpServer.start(deviceName, context, pcUsername, pcPassword)
                                    when (ftpStartResult.status) {
                                        SUCCESS, ALREADY_RUNNING -> {
                                            val ftpInfo = ftpStartResult.serverInfo
                                            if (ftpInfo != null) {
                                                Logger.i(TAG, "ftp 服务器启动成功，IP: ${ftpInfo.ipAddress}, 端口: ${ftpInfo.port}")
                                                val responseJson = JSONObject().apply {
                                                    put("action", "started")
                                                    put("ipAddress", ftpInfo.ipAddress)
                                                    put("port", ftpInfo.port)
                                                    // 不再发送用户名和密码，PC端可以从sharedSecret独立计算
                                                }
                                                Logger.d(TAG, "发送 FTP 服务器信息到 PC")
                                                deviceManager.resolveDeviceInfo(remoteUuid, clientIp, 23333)?.let {
                                                    ProtocolSender.sendEncrypted(
                                                        deviceManager,
                                                        it,
                                                        "DATA_FTP",
                                                        responseJson.toString()
                                                    )
                                                }
                                                Logger.i(TAG, "FTP server started and info sent to PC (derived from sharedSecret)")
                                                
                                                // 检查是否需要跳转到引导页授权
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                    if (!Environment.isExternalStorageManager()) {
                                                        // 跳转到引导页，让用户手动授权
                                                        val intent = IntentUtils.createIntent(context, GuideActivity::class.java)
                                                        intent.putExtra("fromftp", true)
                                                        intent.putExtra("fromInternal", true)
                                                        IntentUtils.startActivity(context, intent, true)
                                                    }
                                                }
                                            }
                                        }

                                        PERMISSION_DENIED -> {
                                            Logger.i(TAG, "ftp 服务器启动失败：权限被拒绝")
                                            val errorMessage = "FTP服务器启动失败：权限被拒绝"
                                            val responseJson = JSONObject().apply {
                                                put("originalHeader", "DATA_FTP")
                                                put("action", "start")
                                                put("result", "error")
                                                put("errorCode", "PERMISSION_DENIED")
                                                put("errorMessage", errorMessage)
                                            }
                                            deviceManager.resolveDeviceInfo(remoteUuid, clientIp, 23333)?.let {
                                                ProtocolSender.sendEncrypted(
                                                    deviceManager,
                                                    it,
                                                    "DATA_STATUS",
                                                    responseJson.toString()
                                                )
                                            }
                                        }
                                        PORT_IN_USE -> {
                                            Logger.i(TAG, "ftp 服务器启动失败：端口被占用")
                                            val errorMessage = "FTP服务器启动失败：端口被占用"
                                            val responseJson = JSONObject().apply {
                                                put("originalHeader", "DATA_FTP")
                                                put("action", "start")
                                                put("result", "error")
                                                put("errorCode", "PORT_IN_USE")
                                                put("errorMessage", errorMessage)
                                            }
                                            deviceManager.resolveDeviceInfo(remoteUuid, clientIp, 23333)?.let {
                                                ProtocolSender.sendEncrypted(
                                                    deviceManager,
                                                    it,
                                                    "DATA_STATUS",
                                                    responseJson.toString()
                                                )
                                            }
                                        }
                                        CONFIG_ERROR -> {
                                            Logger.i(TAG, "ftp 服务器启动失败：配置错误")
                                            val errorMessage = "FTP服务器启动失败：配置错误"
                                            val responseJson = JSONObject().apply {
                                                put("originalHeader", "DATA_FTP")
                                                put("action", "start")
                                                put("result", "error")
                                                put("errorCode", "CONFIG_ERROR")
                                                put("errorMessage", errorMessage)
                                            }
                                            deviceManager.resolveDeviceInfo(remoteUuid, clientIp, 23333)?.let {
                                                ProtocolSender.sendEncrypted(
                                                    deviceManager,
                                                    it,
                                                    "DATA_STATUS",
                                                    responseJson.toString()
                                                )
                                            }
                                        }
                                        FAILED -> {
                                            Logger.i(TAG, "ftp 服务器启动失败：未知错误")
                                            val errorMessage = "FTP服务器启动失败：未知错误"
                                            val responseJson = JSONObject().apply {
                                                put("originalHeader", "DATA_FTP")
                                                put("action", "start")
                                                put("result", "error")
                                                put("errorCode", "FAILED")
                                                put("errorMessage", errorMessage)
                                            }
                                            deviceManager.resolveDeviceInfo(remoteUuid, clientIp, 23333)?.let {
                                                ProtocolSender.sendEncrypted(
                                                    deviceManager,
                                                    it,
                                                    "DATA_STATUS",
                                                    responseJson.toString()
                                                )
                                            }
                                        }
                                    }
                                }

                                "stop" -> {
                                    Logger.i(TAG, "停止 ftp 服务器")
                                    ftpServer.stop()
                                    val responseJson = JSONObject().apply {
                                        put("action", "stopped")
                                    }
                                    deviceManager.resolveDeviceInfo(remoteUuid, clientIp, 23333)?.let {
                                        ProtocolSender.sendEncrypted(
                                            deviceManager,
                                            it,
                                            "DATA_FTP",
                                            responseJson.toString()
                                        )
                                    }
                                    Logger.i(TAG, "FTP server stopped via command")
                                }
                                else -> {
                                    Logger.w(TAG, "未知的 ftp action: $action")
                                }
                            }
                        } catch (e: Exception) {
                            Logger.e(TAG, "处理 ftp 命令失败", e)
                        }
                    }
                    true
                }
                "DATA_CLIPBOARD" -> {
                    Logger.d(TAG, "接收到 DATA_CLIPBOARD 消息: uuid=$remoteUuid, size=${decrypted.length}")
                    // 处理剪贴板消息
                    ClipboardProcessor.process(
                        context,
                        ClipboardProcessor.ClipboardInput(
                            header = "DATA_CLIPBOARD",
                            rawData = decrypted,
                            remoteUuid = remoteUuid
                        )
                    )
                    true
                }
                "DATA_STATUS" -> {
                    Logger.d(TAG, "接收到 DATA_STATUS 消息: uuid=$remoteUuid, size=${decrypted.length}")
                    val routedHeader = "DATA_STATUS"
                    StatusProcessor.process(
                        context,
                        deviceManager,
                        deviceManager.coroutineScopeInternal,
                        StatusProcessor.StatusInput(
                            header = routedHeader,
                            rawData = decrypted,
                            remoteUuid = remoteUuid
                        ),
                        deviceManager.notificationDataReceivedCallbacksInternal
                    )
                    true
                }
                "DATA_APP_LAUNCH" -> {
                    Logger.d(TAG, "接收到 DATA_APP_LAUNCH 消息: uuid=$remoteUuid, size=${decrypted.length}")
                    val source = deviceManager.resolveDeviceInfo(remoteUuid, clientIp, 23333)
                    try {
                        source?.let { AppLaunchManager.handleAppLaunchRequest(decrypted, deviceManager, it, context) }
                    } catch (e: Exception) {
                        Logger.e(TAG, "调用 AppLaunchManager.handleAppLaunchRequest 异常", e)
                    }
                    true
                }
                else -> {
                    // 其他未识别的 DATA_* 报文：当前版本不支持，直接忽略（方便后向兼容）
                    Logger.d(TAG, "未知DATA通道: $header")
                    true
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "路由处理异常: header=$header, uuid=$remoteUuid", e)
            true
        }
    }

    // 解密逻辑已由 DeviceConnectionManager 直接提供，无需反射

    /**
     * 发送媒体控制响应
     */
    private fun sendMediaControlResponse(
        deviceManager: DeviceConnectionManager,
        remoteUuid: String,
        clientIp: String,
        action: String,
        result: String,
        errorMessage: String?
    ) {
        try {
            val responseJson = JSONObject().apply {
                put("originalHeader", "DATA_MEDIA_CONTROL")
                put("action", action)
                put("result", result)
                if (errorMessage != null) {
                    put("errorMessage", errorMessage)
                }
            }
            deviceManager.resolveDeviceInfo(remoteUuid, clientIp, 23333)?.let {
                ProtocolSender.sendEncrypted(
                    deviceManager,
                    it,
                    "DATA_STATUS",
                    responseJson.toString()
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, "发送媒体控制响应失败", e)
        }
    }
}
