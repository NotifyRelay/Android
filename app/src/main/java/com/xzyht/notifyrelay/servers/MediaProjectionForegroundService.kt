package com.xzyht.notifyrelay.servers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class MediaProjectionForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        val channelId = "media_projection_fg"
        val channel = NotificationChannel(
            channelId, "屏幕捕获", NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("屏幕捕获")
            .setContentText("正在通过屏幕捕获获取音频…")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(1002, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // onCreate 已完成 startForeground，此时服务已注册为 mediaProjection 前台服务，
        // 通知外部逻辑可以安全调用 getMediaProjection（Android 14+ 的硬性要求）
        onForegroundReady?.invoke()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        var onForegroundReady: (() -> Unit)? = null

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaProjectionForegroundService::class.java))
        }
    }
}
