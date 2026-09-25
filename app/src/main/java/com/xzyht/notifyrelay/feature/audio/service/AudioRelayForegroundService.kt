package com.xzyht.notifyrelay.feature.audio.service

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.xzyht.notifyrelay.feature.notification.superisland.data.SuperIslandStructuredDataHelper

class AudioRelayForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "音频中继",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME) ?: "设备"
        val direction = intent?.getStringExtra(EXTRA_DIRECTION) ?: "recv"

        val title = if (direction == "send") "正在发送音频" else "正在接收音频"
        val text = if (direction == "send") "正在向 $deviceName 发送音频" else "正在从 $deviceName 接收音频"

        val stopIntent =
            Intent(STOP_ACTION).apply {
                setPackage(packageName)
                putExtra(EXTRA_DEVICE_NAME, deviceName)
                // 超级岛规范附录 2.1：触发广播的 PendingIntent 需要前台调度标志
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
        val pendingIntent =
            PendingIntent.getBroadcast(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_media_play)
                .setOngoing(true)
                .setShowWhen(false)
        // 按「设置 → 超级岛 → 规范信息注入方式」注入规范信息；按钮按模式互斥注册（超级岛走 miui.focus.actions，其余走原生 addAction）
        SuperIslandStructuredDataHelper.applyLocalNotificationSpecInjection(
            builder = builder,
            context = this,
            title = title,
            text = text,
            action =
                SuperIslandStructuredDataHelper.LocalNotificationAction(
                    iconRes = R.drawable.ic_media_pause,
                    title = "停止",
                    pendingIntent = pendingIntent,
                ),
        )
        val notification = builder.build()
        val foregroundType =
            if (direction == "send") {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            }
        try {
            ServiceCompat.startForeground(this, NOTIFY_ID, notification, foregroundType)
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "audio_relay_fg"
        private const val NOTIFY_ID = 1003
        const val STOP_ACTION = "com.xzyht.notifyrelay.STOP_AUDIO_RELAY"
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_DIRECTION = "direction"

        fun start(
            context: Context,
            deviceName: String,
            direction: String,
        ) {
            val intent =
                Intent(context, AudioRelayForegroundService::class.java).apply {
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
