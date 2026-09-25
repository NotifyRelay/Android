package com.xzyht.notifyrelay.feature.media.service

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.xzyht.notifyrelay.feature.notification.superisland.data.SuperIslandStructuredDataHelper

class MediaProjectionForegroundService : Service() {
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        val channelId = "media_projection_fg"
        val channel =
            NotificationChannel(
                channelId,
                "屏幕捕获",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)

        val title = "屏幕捕获"
        val text = "正在通过屏幕捕获获取音频…"
        val builder =
            NotificationCompat
                .Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_menu_camera)
        // 按「设置 → 超级岛 → 规范信息注入方式」注入规范信息（超级岛 / Live Updates 二选一）
        SuperIslandStructuredDataHelper.applyLocalNotificationSpecInjection(
            builder = builder,
            context = this,
            title = title,
            text = text,
        )
        try {
            startForeground(1002, builder.build())
            foregroundStarted = true
        } catch (_: Exception) {
            stopSelf()
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (!foregroundStarted) return START_NOT_STICKY
        // onCreate 已完成 startForeground，此时服务已注册为 mediaProjection 前台服务，
        // 通知外部逻辑可以安全调用 getMediaProjection（Android 14+ 的硬性要求）
        markReady()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        synchronized(lock) {
            isForegroundReady = false
            callbackInvoked = false
            onForegroundReady = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private val lock = Any()

        @Volatile
        private var isForegroundReady = false

        @Volatile
        private var callbackInvoked = false

        var onForegroundReady: (() -> Unit)? = null
            set(value) {
                field = value
                synchronized(lock) {
                    if (value != null && isForegroundReady && !callbackInvoked) {
                        callbackInvoked = true
                        value.invoke()
                    }
                }
            }

        private fun markReady() {
            synchronized(lock) {
                isForegroundReady = true
                if (!callbackInvoked) {
                    callbackInvoked = true
                    onForegroundReady?.invoke()
                }
            }
        }

        fun stop(context: Context) {
            synchronized(lock) {
                isForegroundReady = false
                callbackInvoked = false
                onForegroundReady = null
            }
            context.stopService(Intent(context, MediaProjectionForegroundService::class.java))
        }
    }
}
