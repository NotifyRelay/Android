package com.xzyht.notifyrelay.feature.notification.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.xzyht.notifyrelay.R
import com.xzyht.notifyrelay.feature.clipboard.ClipboardSyncManager
import com.xzyht.notifyrelay.feature.clipboard.ClipboardSyncReceiver
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.notification.SuperIslandListManager
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

        /** 超级岛列表模式下「可切换」提示文案；仅在列表模式且可切换条目 ≥ 2 时参与轮换。 */
        private const val SUPER_ISLAND_SWITCH_HINT = "点击超级岛通知切换通知外显"

        /** 两段文案的轮换周期（毫秒）。 */
        private const val ROTATE_INTERVAL_MS = 5_000L

        /**
         * 当前存活的前台控制器（弱生命周期：服务销毁时置空）。
         *
         * 超级岛列表条目增删不在已有的设备状态流 / 网络回调覆盖范围内，
         * 由 replica 包在条目变化后通过 [onSuperIslandListChanged] 反向触发刷新。
         */
        @Volatile
        private var activeController: ListenerForegroundController? = null

        /**
         * 超级岛列表条目增删/清空后调用：刷新前台通知并同步轮换状态。
         * 前台服务未存活时静默忽略。
         */
        fun onSuperIslandListChanged() {
            activeController?.updateNotification()
        }
    }

    val channelId = "notifyrelay_foreground"
    val notifyId = 1001

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Wake Lock：锁屏期间保持 CPU 不休眠，确保心跳线程正常运行
    private var wakeLock: PowerManager.WakeLock? = null

    // 正文轮换游标：0 = 设备/网络文案，1 = 超级岛切换提示；仅在可切换状态下推进
    private val rotateHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var rotateIndex = 0

    @Volatile
    private var rotationActive = false

    private val rotateRunnable =
        object : Runnable {
            override fun run() {
                if (!shouldShowSuperIslandHint()) {
                    rotationActive = false
                    rotateIndex = 0
                    return
                }
                rotateIndex = (rotateIndex + 1) % 2
                updateNotification()
                rotateHandler.postDelayed(this, ROTATE_INTERVAL_MS)
            }
        }

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

        activeController = this
        syncRotation()
    }

    fun updateNotification() {
        try {
            syncRotation()
            val notification = buildNotification()
            notificationManager.notify(notifyId, notification)
            Logger.d(TAG, "updateNotification: ${getNotificationText()}")
        } catch (e: Exception) {
            Logger.e(TAG, "更新通知失败", e)
        }
    }

    /**
     * 服务销毁时调用：停止轮换定时器并注销静态引用，
     * 避免下一次列表变化打到已脱离前台服务、通知已不存在的控制器上。
     */
    fun dispose() {
        rotationActive = false
        rotateHandler.removeCallbacks(rotateRunnable)
        if (activeController === this) {
            activeController = null
        }
    }

    /** 是否需要展示超级岛切换提示：列表模式开启且可切换条目 ≥ 2。 */
    private fun shouldShowSuperIslandHint(): Boolean =
        try {
            !SuperIslandConfigUtils.isFloatingWindowEnabled(context) &&
                SuperIslandConfigUtils.isNotificationListMode(context) &&
                SuperIslandListManager.size() >= 2
        } catch (_: Exception) {
            false
        }

    /**
     * 同步轮换定时器状态（每次刷新通知时调用）：
     * 满足条件则确保定时器已排定，不满足则停止并把正文退回设备/网络文案。
     */
    private fun syncRotation() {
        if (shouldShowSuperIslandHint()) {
            if (!rotationActive) {
                rotationActive = true
                rotateHandler.postDelayed(rotateRunnable, ROTATE_INTERVAL_MS)
            }
        } else {
            if (rotationActive) {
                rotationActive = false
                rotateHandler.removeCallbacks(rotateRunnable)
            }
            if (rotateIndex != 0) {
                rotateIndex = 0
            }
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

    /**
     * 获取 WakeLock（无超时，绑定前台服务生命周期）。
     *
     * **为什么不加自动超时**（有意保留，勿顺手加 `acquire(timeout)`）：
     * - 释放点已完整覆盖：`NotifyRelayNotificationListenerService` 的 `onDestroy`
     *   与 `onListenerDisconnected` 都会调用 [releaseWakeLock]；
     * - `acquire()` 位于 `startForeground()` 之后（见 [startForegroundService]），
     *   故调用方在启动前台服务阶段抛异常时，不会留下已获取但无人释放的锁；
     * - 即使进程被系统杀死，Android 也会随进程回收其持有的 WakeLock。
     *
     * 若改为超时自动释放，会导致服务仍在前台时中途丢失唤醒锁、心跳线程可能被挂起，
     * 属行为变更而非纯加固，故不采用。
     */
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
                // 轮换（5s 一换）会反复 notify；不加该标记会让 IMPORTANCE_HIGH 渠道每次都响铃/横幅
                .setOnlyAlertOnce(true)
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
        // 列表模式且有多条超级岛可切换时，正文在「设备/网络文案」与「切换外显提示」之间轮换（5s）；
        // 两段文案共用同一个通知，点击行为始终是原有的剪贴板同步，不随文案变化。
        if (rotateIndex == 1 && shouldShowSuperIslandHint()) {
            return SUPER_ISLAND_SWITCH_HINT
        }
        return getDeviceNotificationText()
    }

    private fun getDeviceNotificationText(): String {
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
