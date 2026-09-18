package com.xzyht.notifyrelay.feature.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.xzyht.notifyrelay.feature.device.model.DeviceInfo
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.notification.superisland.replica.FloatingReplicaManager
import com.xzyht.notifyrelay.feature.notification.superisland.store.SuperIslandRemoteStore
import com.xzyht.notifyrelay.nativecore.NativeCore
import com.xzyht.notifyrelay.sync.ProtocolSender
import github.xzynine.superislandui.model.components.MediaSessionData
import io.github.miuzarte.scrcpyforandroid.services.AudioForwardingService
import notifyrelay.base.util.Logger
import notifyrelay.data.StorageManager
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object RemoteMediaSessionManager {
    private const val KEY_ENABLED = "remote_media_island_enabled"
    private const val DEFAULT_ENABLED = true
    private const val KEY_RECEIVE_MODE = "remote_media_message_receive_mode"
    private const val MODE_ON = 0
    private const val MODE_OFF = 1
    private const val MODE_AUDIO_ONLY = 2

    // 会话和设备信息，需要线程安全访问
    @Volatile
    private var currentSession: MediaSessionData? = null

    @Volatile
    private var currentDevice: DeviceInfo? = null

    private var isEnabled: Boolean = true

    // 应用上下文，用于定期检查任务
    private var applicationContext: Context? = null

    // 固定sourceKey前缀，以设备为单位
    private const val SOURCE_KEY_PREFIX = "media_island"

    // 媒体会话特征ID缓存，用于sourceId计算
    private val mediaFeatureIdCache = ConcurrentHashMap<String, String>()

    // 媒体会话最后更新时间缓存
    private val mediaLastUpdateTime = ConcurrentHashMap<String, Long>()

    // 媒体会话数据缓存，用于定时复传（与 MediaSessionResender 共用同一份，保证 map 与 handler 同源）
    private val mediaSessionCache = ConcurrentHashMap<String, MediaSessionCacheDataHolder>()

    // 超时时间（毫秒）统一由 MediaSessionTimeoutCleaner.kt 顶层的 MEDIA_SESSION_TIMEOUT_MS 提供，
    // 复传守卫与清理扫描共用此值，避免两处定义脱钩。

    // 用于处理延迟任务的Handler（所有操作都在主线程串行执行）
    private val handler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        // 保存应用上下文
        applicationContext = context.applicationContext
        // 绑定清理循环宿主（提供同源 handler / 上下文 / 缓存与单设备拆卸实现）
        MediaSessionTimeoutCleaner.bind(
            object : MediaSessionTimeoutCleaner.CleanupHost {
                override val handler: Handler
                    get() = this@RemoteMediaSessionManager.handler

                override val applicationContext: Context?
                    get() = this@RemoteMediaSessionManager.applicationContext

                override val mediaLastUpdateTime: ConcurrentHashMap<String, Long>
                    get() = this@RemoteMediaSessionManager.mediaLastUpdateTime

                override fun closeSessionByUuid(deviceUuid: String) {
                    this@RemoteMediaSessionManager.closeSessionByUuid(deviceUuid)
                }
            },
        )

        val mode = getReceiveMode(context)
        isEnabled = mode != MediaMessageReceiveMode.Off
        Logger.i("RemoteMediaSessionManager", "远端媒体超级岛接收模式: $mode")
    }

    fun isEnabled(context: Context): Boolean = getReceiveMode(context) != MediaMessageReceiveMode.Off

    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        setReceiveMode(context, if (enabled) MediaMessageReceiveMode.On else MediaMessageReceiveMode.Off)
    }

    fun getReceiveMode(context: Context): MediaMessageReceiveMode {
        val stored =
            try {
                StorageManager.getInt(context, KEY_RECEIVE_MODE, -1)
            } catch (_: Exception) {
                -1
            }

        if (stored == -1) {
            val enabled =
                try {
                    StorageManager.getBoolean(context, KEY_ENABLED, DEFAULT_ENABLED)
                } catch (_: Exception) {
                    DEFAULT_ENABLED
                }
            return if (enabled) MediaMessageReceiveMode.On else MediaMessageReceiveMode.Off
        }

        return when (stored) {
            MODE_OFF -> MediaMessageReceiveMode.Off
            MODE_AUDIO_ONLY -> MediaMessageReceiveMode.AudioOnly
            else -> MediaMessageReceiveMode.On
        }
    }

    fun setReceiveMode(
        context: Context,
        mode: MediaMessageReceiveMode,
    ) {
        try {
            val value =
                when (mode) {
                    MediaMessageReceiveMode.On -> MODE_ON
                    MediaMessageReceiveMode.Off -> MODE_OFF
                    MediaMessageReceiveMode.AudioOnly -> MODE_AUDIO_ONLY
                }
            StorageManager.putInt(context, KEY_RECEIVE_MODE, value)
            StorageManager.putBoolean(context, KEY_ENABLED, mode != MediaMessageReceiveMode.Off)
            isEnabled = mode != MediaMessageReceiveMode.Off
            if (mode == MediaMessageReceiveMode.Off) {
                clearSession()
            }
            Logger.i("RemoteMediaSessionManager", "远端媒体超级岛接收模式已设置为: $mode")
        } catch (e: Exception) {
            Logger.e("RemoteMediaSessionManager", "设置远端媒体超级岛接收模式失败", e)
        }
    }

    private fun shouldReceiveMediaMessage(context: Context): Boolean =
        when (getReceiveMode(context)) {
            MediaMessageReceiveMode.On -> true
            MediaMessageReceiveMode.Off -> false
            MediaMessageReceiveMode.AudioOnly -> AudioForwardingService.isAudioForwardingRunning()
        }

    fun onMediaMessageReceived(
        context: Context,
        json: JSONObject,
        device: DeviceInfo,
    ) {
        // 惰性初始化：init() 无外部调用点，首次收到消息时兜底执行，
        // 保证 MediaSessionTimeoutCleaner.bind() 必然触发（host 非空），避免清理链路静默失效。
        // init() 内部操作幂等（设 applicationContext + bind + 读 receiveMode + 日志），重复调用无害。
        if (applicationContext == null) {
            init(context)
        }
        // 所有入口逻辑都串行在 handler 上执行，保护会话状态读写和清理循环调度
        handler.post {
            processMediaMessageOnHandler(context, json, device)
        }
    }

    private fun processMediaMessageOnHandler(
        context: Context,
        json: JSONObject,
        device: DeviceInfo,
    ) {
        if (!shouldReceiveMediaMessage(context)) {
            Logger.d("RemoteMediaSessionManager", "远端媒体消息未接收或未满足音频条件，伪造结束以关闭浮窗")
            closeSessionForDevice(device, "接收条件不满足")
            return
        }

        try {
            val mediaType = json.optString("mediaType", "")
            val terminateValue = json.optString("terminateValue", "")
            val isEndPackage = mediaType.equals("END", true) || terminateValue.equals("__END__", true)

            val sourceKey = SOURCE_KEY_PREFIX + "_" + device.uuid

            if (isEndPackage) {
                Logger.i("RemoteMediaSessionManager", "收到媒体会话结束包，关闭浮窗: ${device.displayName}")
                closeSessionForDevice(device, "收到结束包")
                return
            }

            val packageName = json.optString("packageName", "")
            val appName = json.optString("appName", "")
            val title = json.optString("title", "")
            val text = json.optString("text", "")
            val coverUrl = json.optString("coverUrl", "")
            val timestamp = json.optLong("time", System.currentTimeMillis())

            if (title.isBlank() && text.isBlank()) {
                Logger.w("RemoteMediaSessionManager", "收到空的媒体会话数据，继续处理以保持浮窗活跃")
            }

            val oldSession = mediaSessionCache[device.uuid]?.session
            val finalTitle = title.ifBlank { oldSession?.title ?: "" }
            val finalText = text.ifBlank { oldSession?.text ?: "" }
            val finalCoverUrl = coverUrl.ifBlank { oldSession?.coverUrl }

            currentSession =
                MediaSessionData(
                    packageName = packageName,
                    appName = appName,
                    title = finalTitle,
                    text = finalText,
                    coverUrl = finalCoverUrl,
                    deviceName = device.displayName,
                    timestamp = timestamp,
                )
            currentDevice = device

            val currentFeatureId =
                NativeCore.computeFeatureId(
                    packageName,
                    "",
                    title,
                    text,
                    "",
                ) ?: ""

            mediaFeatureIdCache[device.uuid] = currentFeatureId
            mediaLastUpdateTime[device.uuid] = System.currentTimeMillis()
            MediaSessionTimeoutCleaner.cleanupTimeoutSessionsOnHandler(context)
            MediaSessionResender.setupResendTask(
                handler = handler,
                mediaLastUpdateTime = mediaLastUpdateTime,
                mediaSessionCache = mediaSessionCache,
                sourceKeyPrefix = SOURCE_KEY_PREFIX,
                context = context,
                deviceUuid = device.uuid,
                session = currentSession!!,
                device = device,
                timeoutMs = MEDIA_SESSION_TIMEOUT_MS,
            )
            // 有活跃会话，确保清理循环运行（已在 handler 线程，直接执行）
            MediaSessionTimeoutCleaner.ensureCleanupLoopOnHandler()

            val currentState = MediaStateApplier.buildMediaState(finalTitle, finalText, finalCoverUrl)
            MediaStateApplier.applyMediaSessionState(sourceKey, currentState, appName, context)

            Logger.i("RemoteMediaSessionManager", "更新远端媒体会话: $title - $text (来自 ${device.displayName})")
        } catch (e: Exception) {
            Logger.e("RemoteMediaSessionManager", "处理远端媒体消息失败", e)
        }
    }

    fun clearSession() {
        // 清除所有设备的媒体会话浮窗
        mediaFeatureIdCache.keys.forEach { deviceUuid ->
            val sourceKey = SOURCE_KEY_PREFIX + "_" + deviceUuid
            try {
                // 取消复传任务并拆卸会话（统一实现，保证单一来源）
                closeSessionByUuid(deviceUuid)
                Logger.i("RemoteMediaSessionManager", "已关闭设备媒体超级岛浮窗: $sourceKey")
            } catch (e: Exception) {
                Logger.e("RemoteMediaSessionManager", "关闭媒体超级岛浮窗失败: $sourceKey", e)
            }
        }
        mediaFeatureIdCache.clear()
        mediaLastUpdateTime.clear()
        mediaSessionCache.clear()
        currentSession = null
        currentDevice = null
        MediaSessionTimeoutCleaner.stopCleanupLoop()
        Logger.i("RemoteMediaSessionManager", "已清除所有远端媒体会话")
    }

    private fun closeSessionForDevice(
        device: DeviceInfo,
        reason: String,
    ) {
        closeSessionByUuid(device.uuid)
        Logger.i("RemoteMediaSessionManager", "已关闭设备媒体超级岛浮窗: ${device.displayName}, reason=$reason")
    }

    /**
     * 关闭单个设备会话的统一拆卸（供清理循环与 closeSessionForDevice 共用，并作为 CleanupHost 实现）。
     * 取消复传、移除 Store、关闭浮窗、清三个 map、清当前指针，保持原时序语义不变。
     */
    fun closeSessionByUuid(deviceUuid: String) {
        val sourceKey = SOURCE_KEY_PREFIX + "_" + deviceUuid
        try {
            // 取消复传任务（依赖同源 handler + 同源 mediaSessionCache）
            MediaSessionResender.cancelResendTask(handler, mediaSessionCache, deviceUuid)
            // 从Store中移除
            SuperIslandRemoteStore.removeExact(sourceKey)
            // 关闭浮窗
            FloatingReplicaManager
                .dismissBySource(sourceKey)
            // 清除缓存
            mediaFeatureIdCache.remove(deviceUuid)
            mediaLastUpdateTime.remove(deviceUuid)
            mediaSessionCache.remove(deviceUuid)
            // 如果是当前会话，清除当前会话
            if (currentDevice?.uuid == deviceUuid) {
                currentSession = null
                currentDevice = null
            }
        } catch (e: Exception) {
            Logger.e("RemoteMediaSessionManager", "关闭媒体超级岛浮窗失败: $sourceKey", e)
        }
    }

    fun getCurrentSession(): MediaSessionData? = currentSession

    fun getCurrentDevice(): DeviceInfo? = currentDevice

    fun sendMediaControl(
        context: Context,
        deviceManager: DeviceConnectionManager,
        action: String,
    ) {
        val device = currentDevice ?: return

        try {
            val raw =
                JSONObject()
                    .apply {
                        put("type", "MEDIA_CONTROL")
                        put("action", action)
                    }.toString()
            ProtocolSender.sendEncrypted(deviceManager, device, "DATA_MEDIA_CONTROL", raw)
            Logger.i("RemoteMediaSessionManager", "已发送媒体控制指令: $action 到 ${device.displayName}")
        } catch (e: Exception) {
            Logger.e("RemoteMediaSessionManager", "发送媒体控制指令失败", e)
        }
    }

    fun onPlayPause(
        context: Context,
        deviceManager: DeviceConnectionManager,
    ) {
        sendMediaControl(context, deviceManager, "playPause")
    }

    fun onPrevious(
        context: Context,
        deviceManager: DeviceConnectionManager,
    ) {
        sendMediaControl(context, deviceManager, "previous")
    }

    fun onNext(
        context: Context,
        deviceManager: DeviceConnectionManager,
    ) {
        sendMediaControl(context, deviceManager, "next")
    }
}
