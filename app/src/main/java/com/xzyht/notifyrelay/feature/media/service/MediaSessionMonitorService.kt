package com.xzyht.notifyrelay.feature.media.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import com.xzyht.notifyrelay.feature.media.lyric.LyricResolver
import com.xzyht.notifyrelay.feature.notification.service.NotifyRelayNotificationListenerService
import notifyrelay.base.util.Logger
import java.util.Objects

class MediaSessionMonitorService(
    private val service: NotificationListenerService,
) {
    companion object {
        private const val TAG = "MediaSessionMonitorService"
        var instance: MediaSessionMonitorService? = null
    }

    // 歌词类型（LyricField / LyricState / LyricFieldProbe）已抽至 feature.media.lyric 包

    // 服务连接状态
    private var isConnected = false

    private var mediaSessionManager: MediaSessionManager? = null
    private var componentName: ComponentName? = null

    // 去重：跟踪最后一个元数据哈希值，避免重复处理
    private var lastMetadataHash: Int = 0
    private var lastComputedIsPlaying: Boolean? = null

    // 防抖令牌
    private val updateToken = Any()

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            handler.removeCallbacksAndMessages(updateToken)
            val r = Runnable { controllerRegistry.refreshControllers(controllers) }
            handler.postAtTime(r, updateToken, SystemClock.uptimeMillis())
        }

    private val handler = Handler(Looper.getMainLooper())

    // 歌词字段判定与复核调度（see feature.media.lyric.LyricResolver）。
    // 通过注入的回调将原上报入口与 getPrimaryController 反向依赖解耦到宿主。
    private val lyricResolver =
        LyricResolver(
            handler = handler,
            onMediaSessionUpdated = { pkg, title, artist, duration, artBitmap ->
                NotifyRelayNotificationListenerService.onMediaSessionUpdated(pkg, title, artist, duration, artBitmap)
            },
            getPrimaryController = { getPrimaryController() },
        )

    // 媒体控制器注册表：控制器注册/注销、主控制器选择（see MediaControllerRegistry）。
    // lastMetadataHash 的去重与 updateMetadataIfPrimary 的编排仍留在宿主；getPrimaryController
    // 通过注入的方式反向依赖，避免注册表反向耦合宿主。
    private val controllerRegistry =
        MediaControllerRegistry(
            handler = handler,
            isConnected = { isConnected },
            getLastMetadataHash = { lastMetadataHash },
            onMetadataUpdate = { controller -> updateMetadataIfPrimary(controller) },
            onRecheckSessions = { recheckSessions() },
        )

    // 健康检查与重试机制（see MediaSessionHealthMonitor）。
    // isConnected 仍由宿主持有（被 startMonitoring/stopMonitoring 与 updateControllers 读写），
    // 仅在权限丢失的写回通过 onPermissionLost 回调注入回宿主。
    private val healthMonitor =
        MediaSessionHealthMonitor(
            handler = handler,
            getMediaSessionManager = { mediaSessionManager },
            getComponentName = { componentName },
            isConnected = { isConnected },
            onPermissionLost = { isConnected = false },
            onUpdateControllers = { controllers -> controllerRegistry.refreshControllers(controllers) },
        )

    // 初始化方法
    fun initialize() {
        instance = this
        Logger.i(TAG, "initialize called")
    }

    // 开始监控
    fun startMonitoring() {
        isConnected = true
        Logger.i(TAG, "startMonitoring - Service binding initiated")

        mediaSessionManager = service.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        componentName = ComponentName(service, service.javaClass)

        mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)

        // 启动健康检查监控与重试
        healthMonitor.startHealthCheck()
    }

    // 停止监控
    fun stopMonitoring() {
        isConnected = false
        Logger.i(TAG, "stopMonitoring - Service unbound")

        // 停止健康检查与重试
        healthMonitor.stop()
        // 清理已排队的歌词复核任务，避免停止监听后残留任务读取新的 activeControllers
        lyricResolver.cancelRechecks()

        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)

        // 清理所有控制器回调
        controllerRegistry.stop()
    }

    // 销毁方法
    fun destroy() {
        if (instance === this) instance = null
        Logger.i(TAG, "destroy called")
    }

    fun recheckSessions() {
        if (mediaSessionManager != null && componentName != null) {
            try {
                // 强制更新：重置元数据哈希值，以便下次更新立即传播
                lastMetadataHash = 0
                lastComputedIsPlaying = null
                controllerRegistry.resetControllerSignatures() // 重置签名去重
                controllerRegistry.refreshControllers(mediaSessionManager?.getActiveSessions(componentName))
            } catch (e: SecurityException) {
                Logger.w(TAG, "Error refreshing sessions: ${e.message}")
            }
        }
    }

    fun getPrimaryController(): MediaController? = controllerRegistry.getPrimaryController()

    private fun updateMetadataIfPrimary(controller: MediaController) {
        val metadata = controller.metadata ?: return
        val pkg = controller.packageName

        val rawTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val rawArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

        val primary = getPrimaryController() ?: return

        // 只有当这是主控制器时才处理
        if (controller !== primary) return

        val artBitmap =
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val artHash = artBitmap?.hashCode() ?: 0

        val metadataHash = Objects.hash(rawTitle, rawArtist, pkg, duration, artHash)

        if (metadataHash == lastMetadataHash) {
            return
        }
        lastMetadataHash = metadataHash

        // 歌词字段映射：歌词位于 title（多数应用）还是 artist（如 fnos 音乐）
        val position = controller.playbackState?.position ?: -1L
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: ""
        val (mappedTitle, mappedArtist) =
            lyricResolver.resolveLyricField(pkg, rawTitle ?: "", rawArtist ?: "", duration, mediaId, position)

        // 通知 NotifyRelayNotificationListenerService 有新的媒体会话数据
        NotifyRelayNotificationListenerService.onMediaSessionUpdated(
            pkg,
            mappedTitle,
            mappedArtist,
            duration,
            artBitmap,
        )
    }
}
