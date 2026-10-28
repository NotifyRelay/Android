package com.xzyht.notifyrelay.feature.device.service.callback

import android.os.Build
import com.xzyht.notifyrelay.feature.appslist.launch.AppLaunchManager
import com.xzyht.notifyrelay.feature.appslist.sync.AppListSyncManager
import com.xzyht.notifyrelay.feature.appslist.sync.IconSyncManager
import com.xzyht.notifyrelay.feature.clipboard.ClipboardProcessor
import com.xzyht.notifyrelay.feature.media.MediaControlUtil
import com.xzyht.notifyrelay.feature.media.RemoteMediaSessionManager
import com.xzyht.notifyrelay.nativecore.device.service.callback.DataMessagePort
import com.xzyht.notifyrelay.nativecore.device.service.callback.DeviceCallbackHost
import com.xzyht.notifyrelay.sync.FtpServerManager
import com.xzyht.notifyrelay.sync.FtpServerManager.StartResult
import com.xzyht.notifyrelay.sync.notification.NotificationProcessor
import com.xzyht.notifyrelay.sync.notification.StatusProcessor
import com.xzyht.notifyrelay.sync.notification.SuperIslandProcessor
import com.xzyht.notifyrelay.ui.activity.GuideActivity
import kotlinx.coroutines.launch
import notifyrelay.base.log.Logger
import notifyrelay.base.util.IntentUtils
import notifyrelay.base.util.PermissionHelper
import notifyrelay.domain.feature.device.model.DeviceInfo
import org.json.JSONObject

/**
 * Android 侧入站数据报文的业务实现。
 *
 * 报文分发由公共端 [com.xzyht.notifyrelay.nativecore.device.service.callback.DataCallbackHandler] 完成，
 * 本类只承载各业务处理器的调用。
 *
 * 约束：
 * - 回调运行在 JNA 附加线程，耗时/阻塞操作必须投递到 [DeviceCallbackHost.callbackScope]；
 * - 回调内禁止同步调用 `NativeCore.getDeviceList()`（core 重入崩溃）。
 */
class AndroidDataMessagePort(
    private val host: DeviceCallbackHost,
    private val context: android.content.Context,
    private val audioRelay: com.xzyht.notifyrelay.feature.device.service.audio.AudioRelayController,
    private val deviceManager: com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager,
) : DataMessagePort {
    companion object {
        private const val TAG = "CoreCb"
    }

    override fun onNotification(
        deviceUuid: String,
        payload: String,
    ) {
        NotificationProcessor.process(
            context,
            deviceManager,
            host.callbackScope,
            NotificationProcessor.NotificationInput("DATA_NOTIFICATION", payload, deviceUuid),
            host.notificationDataReceivedCallbacks,
        )
    }

    override fun onMediaPlay(
        device: DeviceInfo,
        payload: String,
    ) {
        RemoteMediaSessionManager.onMediaMessageReceived(context, JSONObject(payload), device)
    }

    override fun onIconRequest(
        device: DeviceInfo,
        payload: String,
    ) {
        IconSyncManager.handleIconRequest(payload, deviceManager, device, context)
    }

    override fun onIconResponse(payload: String) {
        IconSyncManager.handleIconResponse(payload, context)
    }

    override fun onAppListRequest(
        device: DeviceInfo,
        payload: String,
    ) {
        AppListSyncManager.handleAppListRequest(payload, deviceManager, device, context)
    }

    override fun onAppListResponse(
        deviceUuid: String,
        payload: String,
    ) {
        AppListSyncManager.handleAppListResponse(payload, context, deviceUuid, deviceManager)
    }

    override fun onMediaControl(
        deviceUuid: String,
        payload: String,
    ) = handleMediaControl(deviceUuid, payload)

    override fun onFtp(
        deviceUuid: String,
        payload: String,
    ) = handleFtp(deviceUuid, payload)

    override fun onClipboard(payload: String) {
        ClipboardProcessor.process(context, ClipboardProcessor.ClipboardInput("DATA_CLIPBOARD", payload, ""))
    }

    override fun onStatus(
        deviceUuid: String,
        payload: String,
    ) {
        StatusProcessor.process(
            context,
            deviceManager,
            host.callbackScope,
            StatusProcessor.StatusInput("DATA_STATUS", payload, deviceUuid),
            host.notificationDataReceivedCallbacks,
        )
    }

    override fun onAppLaunch(
        device: DeviceInfo,
        payload: String,
    ) {
        AppLaunchManager.handleAppLaunchRequest(payload, deviceManager, device, context)
    }

    override fun onSuperIsland(
        deviceUuid: String,
        payload: String,
    ) {
        SuperIslandProcessor.process(context, deviceManager, payload, deviceUuid)
    }

    /** 媒体控制：播放控制直达本机媒体，音频中继动作交由音频控制器。 */
    private fun handleMediaControl(
        uuid: String,
        text: String,
    ) {
        val json = JSONObject(text)
        when (val action = json.getString("action")) {
            "playPause" -> runLocalMediaAction(uuid, action) { MediaControlUtil.playPause() }
            "next" -> runLocalMediaAction(uuid, action) { MediaControlUtil.next() }
            "previous" -> runLocalMediaAction(uuid, action) { MediaControlUtil.previous() }
            "audioRequest" -> host.callbackScope.launch { audioRelay.handleAudioRequest(uuid) }
            "audioResponse" ->
                audioRelay.handleAudioResponse(json.optString("result", "rejected") == "accepted")
            "audioStart" ->
                host.callbackScope.launch {
                    audioRelay.startReceive(
                        remoteUuid = uuid,
                        sampleRate = json.optInt("sampleRate", 48000),
                        channels = json.optInt("channels", 2),
                    )
                }
            "audioStop" ->
                host.callbackScope.launch {
                    audioRelay.stopFromRemote(
                        remoteUuid = uuid,
                        replyAck = json.optString("result", "").isEmpty(),
                    )
                }
        }
    }

    /** 执行本机媒体控制并回执结果（失败原因随回执一并返回远端）。 */
    private fun runLocalMediaAction(
        uuid: String,
        action: String,
        block: () -> Unit,
    ) {
        try {
            block()
            sendMediaControlResponse(uuid, action, "success", null)
        } catch (e: Exception) {
            sendMediaControlResponse(uuid, action, "error", e.message)
        }
    }

    /** 媒体控制响应：以 DATA_STATUS 承载，originalHeader 标明真实来源。 */
    private fun sendMediaControlResponse(
        remoteUuid: String,
        action: String,
        result: String,
        errorMessage: String?,
    ) {
        try {
            val raw =
                JSONObject()
                    .apply {
                        put("originalHeader", "DATA_MEDIA_CONTROL")
                        put("action", action)
                        put("result", result)
                        if (errorMessage != null) put("errorMessage", errorMessage)
                    }.toString()
            host.resolveDevice(remoteUuid)?.let { replyToDevice(it, "DATA_STATUS", raw) }
        } catch (e: Exception) {
            Logger.e(TAG, "sendMediaControlResponse", e)
        }
    }

    /** FTP：仅接受设备类型为 pc 的对端请求（启动/停止本机 FTP 服务）。 */
    private fun handleFtp(
        uuid: String,
        text: String,
    ) {
        val isPc =
            synchronized(host.authenticatedDeviceTable) {
                host.authenticatedDeviceTable[uuid]?.deviceType?.lowercase() == "pc"
            }
        if (!isPc) return
        host.callbackScope.launch {
            try {
                val json = JSONObject(text)
                when (json.optString("action", "")) {
                    "start" -> handleFtpStart(uuid, json)
                    "stop" -> {
                        FtpServerManager.stop()
                        val raw = JSONObject().apply { put("action", "stopped") }.toString()
                        host.resolveDevice(uuid)?.let { replyToDevice(it, "DATA_FTP", raw) }
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "DATA_FTP", e)
            }
        }
    }

    private fun handleFtpStart(
        uuid: String,
        json: JSONObject,
    ) {
        val pcUser: String? = if (json.has("username")) json.optString("username") else null
        val pcPass: String? = if (json.has("password")) json.optString("password") else null
        val result = FtpServerManager.start(host.localDisplayName(), context, pcUser, pcPass)
        when (result.status) {
            StartResult.SUCCESS, StartResult.ALREADY_RUNNING -> {
                result.serverInfo?.let { info ->
                    val raw =
                        JSONObject()
                            .apply {
                                put("action", "started")
                                put("ipAddress", info.ipAddress)
                                put("port", info.port)
                            }.toString()
                    host.resolveDevice(uuid)?.let { replyToDevice(it, "DATA_FTP", raw) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !PermissionHelper.checkManageExternalStoragePermission(context)) {
                        val intent = IntentUtils.createIntent(context, GuideActivity::class.java)
                        intent.putExtra("fromftp", true)
                        intent.putExtra("fromInternal", true)
                        IntentUtils.startActivity(context, intent, true)
                    }
                }
            }
            else -> {
                val err =
                    when (result.status) {
                        StartResult.PERMISSION_DENIED -> "PERMISSION_DENIED"
                        StartResult.PORT_IN_USE -> "PORT_IN_USE"
                        StartResult.CONFIG_ERROR -> "CONFIG_ERROR"
                        else -> "FAILED"
                    }
                val raw =
                    JSONObject()
                        .apply {
                            put("originalHeader", "DATA_FTP")
                            put("action", "start")
                            put("result", "error")
                            put("errorCode", err)
                        }.toString()
                host.resolveDevice(uuid)?.let { replyToDevice(it, "DATA_STATUS", raw) }
            }
        }
    }

    private fun replyToDevice(
        target: DeviceInfo,
        header: String,
        raw: String,
    ) {
        host.sendDataMessage(target, header, raw)
    }
}
