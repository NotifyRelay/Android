package com.xzyht.notifyrelay.servers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class AudioRelayForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID, "音频中继", NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME) ?: "设备"
        val direction = intent?.getStringExtra(EXTRA_DIRECTION) ?: "recv"

        val title = if (direction == "send") "正在发送音频" else "正在接收音频"
        val text = if (direction == "send") "正在向 $deviceName 发送音频" else "正在从 $deviceName 接收音频"

        val stopIntent = Intent(STOP_ACTION).apply {
            putExtra(EXTRA_DEVICE_NAME, deviceName)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, 0, stopIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(android.R.drawable.ic_media_pause, "停止", pendingIntent)
            .build()
        startForeground(NOTIFY_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "audio_relay_fg"
        private const val NOTIFY_ID = 1003
        const val STOP_ACTION = "com.xzyht.notifyrelay.STOP_AUDIO_RELAY"
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_DIRECTION = "direction"

        fun start(context: Context, deviceName: String, direction: String) {
            val intent = Intent(context, AudioRelayForegroundService::class.java).apply {
                putExtra(EXTRA_DEVICE_NAME, deviceName)
                putExtra(EXTRA_DIRECTION, direction)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AudioRelayForegroundService::class.java))
        }
    }
}
