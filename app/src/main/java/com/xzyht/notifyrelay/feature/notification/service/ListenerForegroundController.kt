package com.xzyht.notifyrelay.feature.notification.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.xzyht.notifyrelay.R
import com.xzyht.notifyrelay.feature.clipboard.ClipboardSyncManager
import com.xzyht.notifyrelay.feature.clipboard.ClipboardSyncReceiver
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import notifyrelay.base.util.Logger

/**
 * 通知监听服务的前台常驻通知与 WakeLock 控制器。
 *
 * WakeLock 生命周期绑定前台服务：前台服务启动时获取，
 * `onListenerDisconnected` / `onDestroy` 时释放。
 */
internal class ListenerForegroundController(
    private val context: Context,
    private val connectionManager: DeviceConnectionManager,
    private val startForeground: (Int, Notification) -> Unit,
) {
    companion object {
        private const val TAG = "NotifyRelayNotificationListenerService"
    }

    val channelId = "notifyrelay_foreground"
    val notifyId = 1001

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Wake Lock：锁屏期间保持 CPU 不休眠，确保心跳线程正常运行
    private var wakeLock: PowerManager.WakeLock? = null

    fun startForegroundService() {
        val channel =
            NotificationChannel(
                channelId,
                "通知转发后台服务",
                NotificationManager.IMPORTANCE_HIGH,
            )
        notificationManager.createNotificationChannel(channel)

        val notification = buildNotification()
        startForeground(notifyId, notification)
        acquireWakeLock()
    }

    fun updateNotification() {
        try {
            val notification = buildNotification()
            notificationManager.notify(notifyId, notification)
            Logger.d(TAG, "updateNotification: ${getNotificationText()}")
        } catch (e: Exception) {
            Logger.e(TAG, "更新通知失败", e)
        }
    }

    fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Logger.i(TAG, "Wake Lock 已释放")
            }
        }
        wakeLock = null
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock =
                    pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "notifyrelay:core").apply {
                        acquire()
                    }
                Logger.i(TAG, "Wake Lock 已获取")
            } catch (e: SecurityException) {
                Logger.w(TAG, "获取 Wake Lock 失败（缺少权限）", e)
            }
        }
    }

    private fun buildNotification(): Notification {
        val builder =
            NotificationCompat
                .Builder(context, channelId)
                .setContentTitle("通知监听/转发中")
                .setContentText(getNotificationText())
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

        // 为通知主体添加点击事件，实现剪贴板同步功能
        try {
            val syncIntent =
                Intent(context, ClipboardSyncReceiver::class.java).apply {
                    action = ClipboardSyncReceiver.ACTION_MANUAL_SYNC
                }
            val syncPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    syncIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            builder.setContentIntent(syncPendingIntent)
        } catch (e: Exception) {
            Logger.w(TAG, "添加剪贴板点击事件失败", e)
        }

        return builder.build()
    }

    private fun getNotificationText(): String {
        // 使用 DeviceConnectionManager 提供的线程安全方法获取在线且已认证的设备数量
        val onlineDevices =
            try {
                connectionManager.getAuthenticatedOnlineCount()
            } catch (_: Exception) {
                0
            }
        val fcitx5Paired =
            try {
                ClipboardSyncManager.isFcitx5Paired(context)
            } catch (_: Exception) {
                false
            }
        // Logger.d(TAG, "getNotificationText: authenticatedOnlineCount=$onlineDevices")
        Logger.d(TAG, "getNotificationText: authenticatedOnlineCount=$onlineDevices")

        // 优先显示设备连接数，如果有设备连接
        if (onlineDevices > 0) {
            return if (!fcitx5Paired) {
                "当前${onlineDevices}台设备已连接，点击以同步剪贴板"
            } else {
                "当前${onlineDevices}台设备已连接"
            }
        }

        // 没有设备连接时，显示网络状态
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isEthernet = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val isWifiDirect = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_WIFI_P2P) == true

        // 如果不是WiFi、以太网或WLAN直连，则认为是移动数据等非局域网
        val baseText =
            if (!isWifi && !isEthernet && !isWifiDirect) {
                "非局域网连接"
            } else {
                "无设备在线"
            }

        // Fcitx5 未启用时，添加点击提示
        return if (!fcitx5Paired) {
            "$baseText，点击通知同步剪贴板"
        } else {
            baseText
        }
    }
}
