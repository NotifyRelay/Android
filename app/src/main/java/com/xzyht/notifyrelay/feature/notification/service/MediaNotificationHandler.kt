package com.xzyht.notifyrelay.feature.notification.service

import android.app.Notification
import android.graphics.Bitmap
import android.service.notification.StatusBarNotification
import android.util.Base64
import com.xzyht.notifyrelay.feature.notification.superisland.media.MediaCapsulePresenter
import com.xzyht.notifyrelay.feature.notification.superisland.replica.FloatingReplicaManager
import com.xzyht.notifyrelay.sync.MessageSender
import notifyrelay.base.util.Logger
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * 媒体播放通知处理器。
 *
 * 持有 MediaSession 数据缓存（由 `MediaSessionMonitorService` 经
 * [NotifyRelayNotificationListenerService.onMediaSessionUpdated] 回填），
 * 并负责把媒体通知转换为媒体播放状态包发送给远端。
 *
 * 缓存同时被静态回调入口与服务实例方法读取，因此本处理器为 object 单例，
 * 保证二者访问**同一个**实例。静态入口仍保留在
 * [NotifyRelayNotificationListenerService] 上（签名与可见性不可变），由它转发到此。
 *
 * 可见性为 public：`getMediaSessionData` 被服务的 public 静态方法转发，
 * 返回类型不能是 internal。
 */
object MediaNotificationHandler {
    private const val TAG = "NotifyRelayNotificationListenerService"

    // 媒体会话数据缓存
    private val mediaSessionDataCache = ConcurrentHashMap<String, MediaSessionData>()

    // 接收来自 MediaSessionMonitorService 的媒体会话数据
    fun onMediaSessionUpdated(
        packageName: String,
        title: String,
        artist: String,
        duration: Long,
        artBitmap: Any?,
    ) {
        Logger.i(TAG, "Received MediaSession update: $packageName - $title")

        // 缓存媒体会话数据
        val mediaSessionData =
            MediaSessionData(
                packageName = packageName,
                title = title,
                artist = artist,
                duration = duration,
                artBitmap = artBitmap as? Bitmap,
                timestamp = System.currentTimeMillis(),
            )
        mediaSessionDataCache[packageName] = mediaSessionData

        // 立即处理媒体会话数据，确保歌词获取与通知获取同步
        // 查找对应的媒体通知并处理
        val service = NotifyRelayNotificationListenerService.instance ?: return
        val activeNotifications = service.activeNotifications
        if (activeNotifications != null) {
            for (sbn in activeNotifications) {
                if (sbn.packageName == packageName && sbn.notification.category == Notification.CATEGORY_TRANSPORT) {
                    processMediaNotification(service, sbn)
                    break
                }
            }
        }
    }

    // 获取指定包名的媒体会话数据
    fun getMediaSessionData(packageName: String): MediaSessionData? = mediaSessionDataCache[packageName]

    /**
     * 本机 MediaSession 销毁时，关闭对应 packageName 的胶囊歌词浮窗。
     *
     * 通过 packageName 查找 CATEGORY_TRANSPORT 通知的 sbnKey，再 dismissBySource。
     * 若通知已被移除，[NotifyRelayNotificationListenerService.onNotificationRemoved] 应已先行关闭，
     * 此处找不到活动通知即视为已关闭，无需重复处理。
     */
    fun dismissCapsuleByPackageName(packageName: String) {
        val service = NotifyRelayNotificationListenerService.instance ?: return
        val activeNotifications = service.activeNotifications ?: return
        for (sbn in activeNotifications) {
            if (sbn.packageName == packageName && sbn.notification.category == Notification.CATEGORY_TRANSPORT) {
                val sbnKey = service.getNotificationKey(sbn)
                FloatingReplicaManager.dismissBySource(sbnKey)
                Logger.i(TAG, "MediaSession 销毁，关闭胶囊歌词浮窗: pkg=$packageName, sbnKey=$sbnKey")
                return
            }
        }
        Logger.d(TAG, "MediaSession 销毁，未找到对应媒体通知（可能已移除）: pkg=$packageName")
    }

    /**
     * 处理媒体播放通知
     */
    fun processMediaNotification(
        service: NotifyRelayNotificationListenerService,
        sbn: StatusBarNotification,
    ) {
        val sbnKey = service.getNotificationKey(sbn)
        // 更新全局持有的最新媒体通知，方便外部通过工具类触发操作
        try {
            NotifyRelayNotificationListenerService.latestMediaSbn = sbn
        } catch (_: Exception) {
        }

        // 初始化变量
        var finalTitle: String
        var finalText: String
        var finalCoverUrl: String? = null

        // 使用 MediaSession 机制获取数据
        val mediaSessionData = getMediaSessionData(sbn.packageName)
        if (mediaSessionData != null) {
            Logger.i(TAG, "Using MediaSession data for ${sbn.packageName}")
            // 使用 MediaSession 数据
            finalTitle = mediaSessionData.title
            finalText = mediaSessionData.artist

            // 从 MediaSession 获取封面
            if (mediaSessionData.artBitmap != null) {
                try {
                    val stream = ByteArrayOutputStream()
                    mediaSessionData.artBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                    val bytes = stream.toByteArray()
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    finalCoverUrl = "data:image/jpeg;base64,$base64"
                } catch (e: Exception) {
                    Logger.e(TAG, "获取 MediaSession 封面失败", e)
                }
            }
        } else {
            // 没有 MediaSession 数据，跳过处理
            Logger.d(TAG, "No MediaSession data for ${sbn.packageName}, skipping media notification")
            return
        }

        // 检查胶囊歌词开关状态
        val capsuleLyricsEnabled = service.getStorageBoolean("capsule_lyrics_enabled", false)

        // 如果胶囊歌词开关开启，直接在本机内生成浮窗和通知
        if (capsuleLyricsEnabled) {
            try {
                Logger.i(TAG, "胶囊歌词开关开启，在本机内生成浮窗和通知: title='$finalTitle', text='$finalText'")

                val picMap = mutableMapOf<String, String>()
                if (!finalCoverUrl.isNullOrBlank()) {
                    picMap["miui.focus.pic_cover"] = finalCoverUrl
                    picMap["miui.focus.pic_app_icon"] = finalCoverUrl
                }

                val appName = service.getAppName(sbn.packageName)
                MediaCapsulePresenter.show(
                    context = service.applicationContext,
                    sourceId = sbnKey,
                    title = finalTitle,
                    text = finalText,
                    appName = appName,
                    picMap = picMap,
                )
            } catch (e: Exception) {
                Logger.e(TAG, "在本机内生成浮窗和通知失败", e)
            }
        }

        if (!service.getStorageBoolean("send_media_notifications_enabled", true)) return

        try {
            val appName = service.getAppName(sbn.packageName)
            MessageSender.sendMediaPlayNotification(
                service.applicationContext,
                sbn.packageName,
                appName,
                finalTitle,
                finalText,
                finalCoverUrl,
                sbn.postTime,
                service.deviceManager,
            )
        } catch (e: Exception) {
            Logger.e(TAG, "发送媒体播放消息失败", e)
        }
    }

    // 媒体会话数据类
    data class MediaSessionData(
        val packageName: String,
        val title: String,
        val artist: String,
        val duration: Long,
        val artBitmap: Bitmap?,
        val timestamp: Long,
    )
}
