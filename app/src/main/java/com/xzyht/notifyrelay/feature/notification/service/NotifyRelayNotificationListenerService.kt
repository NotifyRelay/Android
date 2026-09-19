package com.xzyht.notifyrelay.feature.notification.service

import android.app.Notification
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.xzyht.notifyrelay.feature.device.model.NotificationRepository
import com.xzyht.notifyrelay.feature.device.model.NotificationTextReader
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
import com.xzyht.notifyrelay.feature.media.service.MediaSessionMonitorService
import com.xzyht.notifyrelay.feature.notification.filter.BackendLocalFilter
import com.xzyht.notifyrelay.feature.notification.superisland.replica.FloatingReplicaManager
import com.xzyht.notifyrelay.feature.notification.superisland.tracker.LocalSuperIslandTracker
import com.xzyht.notifyrelay.sync.MessageSender
import com.xzyht.notifyrelay.ui.activity.GuideActivity
import github.xzynine.superislandui.common.SuperIslandManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import notifyrelay.base.util.Logger
import notifyrelay.base.util.PermissionHelper
import notifyrelay.base.util.SuperIslandStorageKeys
import notifyrelay.data.StorageManager

class NotifyRelayNotificationListenerService : NotificationListenerService() {
    companion object {
        private const val TAG = "NotifyRelayNotificationListenerService"

        // 最新的媒体播放通知（用于被外部工具查询并触发其 action）
        @Volatile
        var latestMediaSbn: StatusBarNotification? = null

        // 服务实例，用于在静态方法中访问实例方法
        @Volatile
        var instance: NotifyRelayNotificationListenerService? = null

        // 接收来自 MediaSessionMonitorService 的媒体会话数据
        @JvmStatic
        fun onMediaSessionUpdated(
            packageName: String,
            title: String,
            artist: String,
            duration: Long,
            artBitmap: Any?,
        ) {
            MediaNotificationHandler.onMediaSessionUpdated(packageName, title, artist, duration, artBitmap)
        }

        // 获取指定包名的媒体会话数据
        fun getMediaSessionData(packageName: String): MediaNotificationHandler.MediaSessionData? = MediaNotificationHandler.getMediaSessionData(packageName)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // 只补发本应用的前台服务通知（必须channelId和id都匹配）
        if (sbn.packageName == applicationContext.packageName &&
            sbn.notification.channelId == foregroundController.channelId &&
            sbn.id == foregroundController.notifyId
        ) {
            Logger.w(TAG, "前台服务通知被移除，自动补发！")
            // 立即补发本服务前台通知
            startForegroundService()
        } else if (sbn.packageName == applicationContext.packageName) {
            // 检查是否为超级岛相关通知（包括普通超级岛和焦点歌词）
            val channelId = sbn.notification.channelId
            if (channelId == "super_island_replica") {
                // 超级岛相关通知被移除，关闭对应的浮窗条目
                Logger.i(TAG, "超级岛相关通知被移除，关闭对应的浮窗条目: id=${sbn.id}, channelId=$channelId")
                FloatingReplicaManager.closeByNotificationId(sbn.id)
            }
        } else {
            // 普通通知被移除时，从已处理缓存中移除，允许下次重新处理
            val notificationKey = getNotificationKey(sbn, "")
            processedNotifications.remove(notificationKey)
            Logger.v(TAG, "通知移除，从缓存中清理: sbnKey=${sbn.key}, pkg=${sbn.packageName}")

            // 检查是否为媒体通知
            val isMediaNotification = sbn.notification.category == Notification.CATEGORY_TRANSPORT
            if (isMediaNotification) {
                // 媒体通知被移除，发送媒体结束包
                try {
                    Logger.i(TAG, "媒体通知被移除，发送结束包: pkg=${sbn.packageName}")
                    val appName = getAppName(sbn.packageName)
                    // 发送媒体结束包
                    MessageSender.sendMediaPlayEndNotification(
                        applicationContext,
                        sbn.packageName,
                        appName,
                        System.currentTimeMillis(),
                        deviceManager,
                    )
                    // 更新全局最新媒体通知
                    if (latestMediaSbn?.key == sbn.key) {
                        latestMediaSbn = null
                    }
                    // 关闭对应的浮窗条目，像远程通知一样立即结束
                    val sbnKey = getNotificationKey(sbn)
                    FloatingReplicaManager.dismissBySource(sbnKey)
                    Logger.i(TAG, "媒体通知被移除，关闭对应的浮窗条目: sbnKey=$sbnKey")
                } catch (e: Exception) {
                    Logger.e(TAG, "发送媒体结束包失败", e)
                }
            } else {
                // 超级岛：发送终止包
                try {
                    val superData =
                        try {
                            SuperIslandManager.extractSuperIslandData(sbn, applicationContext)
                        } catch (_: Exception) {
                            null
                        }
                    if (superData != null) {
                        val deviceManager = this.deviceManager
                        val superPkg = superData.sourcePackage ?: "unknown"
                        LocalSuperIslandTracker.markInactive(superPkg)
                        MessageSender.sendSuperIslandEnd(
                            applicationContext,
                            superPkg,
                            try {
                                applicationContext.packageName
                            } catch (_: Exception) {
                                null
                            },
                            System.currentTimeMillis(),
                            superData.paramV2Raw,
                            getNotificationTitle(sbn),
                            getNotificationText(sbn),
                            deviceManager,
                            featureIdOverride = getNotificationKey(sbn, ""),
                        )
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Logger.i(TAG, "[NotifyListener] onTaskRemoved called, rootIntent=$rootIntent")
        super.onTaskRemoved(rootIntent)
        // 重新启动服务，防止被系统杀死
        val restartIntent =
            Intent(applicationContext, NotifyRelayNotificationListenerService::class.java)
        applicationContext.startForegroundService(restartIntent)
    }

    override fun onCreate() {
        Logger.i(TAG, "[NotifyListener] onCreate called")
        // 初始化服务实例
        instance = this
        // 注册缓存清理器
        NotificationRepository.registerCacheCleaner { keysToRemove ->
            if (keysToRemove.isEmpty()) {
                // 空集合表示清除全部缓存
                processedNotifications.clear()
            } else {
                // 清除指定的缓存项
                processedNotifications.removeAll(keysToRemove)
            }
        }
        // 确保本地历史缓存已加载，避免首次拉取时判重失效
        NotificationRepository.init(applicationContext)
        // 初始化设备连接管理器并启动发现
        connectionManager = DeviceConnectionManagerSingleton.getDeviceManager(applicationContext)
        foregroundController =
            ListenerForegroundController(applicationContext, connectionManager) { id, notification ->
                startForeground(id, notification)
            }
        try {
            connectionManager.startDiscovery()
        } catch (e: Exception) {
            Logger.w(TAG, "[NotifyListener] 启动设备发现失败", e)
        }

        // 初始化 MediaSession 监控服务
        mediaSessionMonitorService = MediaSessionMonitorService(this)
        mediaSessionMonitorService.initialize()

        // 监听设备状态变化，更新通知
        CoroutineScope(Dispatchers.Default).launch {
            try {
                connectionManager.devices.collect {
                    // 设备状态发生变化时更新通知
                    updateNotification()
                }
            } catch (e: Exception) {
                Logger.e(TAG, "设备状态监听协程异常退出，通知将不再自动更新", e)
            }
        }

        // 监听网络状态变化，更新通知
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    updateNotification()
                }

                override fun onLost(network: Network) {
                    updateNotification()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    updateNotification()
                }
            }
        connectivityManager.registerDefaultNetworkCallback(networkCallback!!)

        super.onCreate()
    }

    override fun onBind(intent: Intent?): IBinder? {
        Logger.i(TAG, "[NotifyListener] onBind called, intent=$intent")
        return super.onBind(intent)
    }

    private var foregroundJob: Job? = null

    // 前台常驻通知与 WakeLock 控制器
    private lateinit var foregroundController: ListenerForegroundController

    // 设备连接管理器
    private lateinit var connectionManager: DeviceConnectionManager
    internal val deviceManager by lazy { DeviceConnectionManagerSingleton.getDeviceManager(applicationContext) }

    // 网络监听器
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // 新增：已处理通知缓存，避免重复处理 (改进版：带时间戳的LRU缓存)
    private val processedNotifications = NotificationProcessedCache()

    // 通知发送专用作用域：串行发送
    private val sendScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sendMutex = Mutex()

    // MediaSession 监控服务实例
    private lateinit var mediaSessionMonitorService: MediaSessionMonitorService

    // 使用通用工具将 Drawable 转换为 Bitmap（参照项目中其他模块的实现）

    /**
     * 处理媒体播放通知
     */
    private fun processMediaNotification(sbn: StatusBarNotification) {
        MediaNotificationHandler.processMediaNotification(this, sbn)
    }

    private fun cleanupExpiredCacheEntries(currentTime: Long) {
        processedNotifications.cleanupExpiredEntries(currentTime)
    }

    private fun processNotification(
        sbn: StatusBarNotification,
        checkProcessed: Boolean = false,
    ) {
        // 读取超级岛设置开关，决定是否按超级岛专用逻辑处理
        val superIslandEnabled = getStorageBoolean(SuperIslandStorageKeys.ENABLED, true)

        // 检查是否为媒体播放通知
        val isMediaNotification = sbn.notification.category == Notification.CATEGORY_TRANSPORT
        if (isMediaNotification) {
            // 媒体播放消息，单独处理
            processMediaNotification(sbn)
            return
        }

        // 在本机本地过滤前，尝试读取超级岛信息并单独转发
        // 当开关开启且检测到超级岛数据时，只发送超级岛分支，不再走普通通知转发
        val superIslandHandledAndStop: Boolean = tryForwardSuperIsland(sbn, superIslandEnabled)

        if (superIslandHandledAndStop) {
            // 超级岛分支已完成，只保留本机历史，不再转发普通通知
            logSbnDetail("超级岛: 已按超级岛分支处理，跳过普通转发", sbn)
            return
        }

        if (!shouldProcess(sbn, checkProcessed)) {
            return
        }

        commitToHistoryAndForward(sbn)
    }

    /**
     * 超级岛检测与转发。
     *
     * 返回 true 表示已按超级岛分支处理完（调用方只保留本机历史，不再走普通转发）；
     * 返回 false 表示继续按普通通知流程处理。
     */
    private fun tryForwardSuperIsland(
        sbn: StatusBarNotification,
        superIslandEnabled: Boolean,
    ): Boolean =
        if (superIslandEnabled) {
            try {
                if (sbn.packageName == applicationContext.packageName) {
                    false
                } else {
                    val superData = SuperIslandManager.extractSuperIslandData(sbn, applicationContext)
                    if (superData != null) {
                        Logger.i(TAG, "超级岛: 检测到超级岛数据，准备转发，pkg=${superData.sourcePackage}, title=${superData.title}")
                        superData.sourcePackage?.let { LocalSuperIslandTracker.markActive(it) }
                        try {
                            val deviceManager = this.deviceManager
                            // 不再使用包名前缀标记；通过通道头 DATA_SUPERISLAND 区分超级岛
                            val superPkg = superData.sourcePackage ?: "unknown"
                            // 严格以通知 sbn.key 作为会话键：一条系统通知只对应一座"岛"，内容变化不影响会话
                            val featureId = getNotificationKey(sbn, "")
                            // 图片处理（本地 URI 读取/Base64）可能在 IO 线程耗时，异步发送避免阻塞监听线程；
                            // sendSuperIslandData 内部已捕获全部异常
                            sendScope.launch {
                                sendMutex.withLock {
                                    MessageSender.sendSuperIslandData(
                                        applicationContext,
                                        superPkg,
                                        superData.appName ?: "超级岛",
                                        superData.title,
                                        superData.text,
                                        sbn.postTime,
                                        superData.paramV2Raw,
                                        // 尝试把 simple pic map 提取为 string map（仅支持 string/url 类值）
                                        (superData.picMap ?: emptyMap()),
                                        deviceManager,
                                        featureIdOverride = featureId,
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Logger.w(TAG, "超级岛: 转发超级岛数据失败: ${e.message}")
                        }
                        true
                    } else {
                        false
                    }
                }
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }

    /**
     * 本地过滤 + 去重缓存判定。
     *
     * 返回 true 表示应当继续转发；false 表示被过滤或已处理过，调用方应直接返回。
     *
     * TTL 判断与缓存更新的顺序不可颠倒。
     */
    private fun shouldProcess(
        sbn: StatusBarNotification,
        checkProcessed: Boolean,
    ): Boolean {
        if (!BackendLocalFilter.shouldForwardBlocking(sbn, applicationContext, checkProcessed)) {
            if (Logger.enableFilteredNotificationLog) {
                logSbnDetail("法鸡-黑影 被过滤", sbn)
            }
            return false
        }
        val notificationKey = sbn.key ?: (sbn.id.toString() + sbn.packageName)
        val currentTime = System.currentTimeMillis()

        // 检查缓存和TTL
        if (checkProcessed && processedNotifications.isRecentlyProcessed(notificationKey, currentTime)) {
            return false
        }

        // 清理过期缓存条目
        cleanupExpiredCacheEntries(currentTime)

        // 更新缓存
        processedNotifications.markProcessed(notificationKey, currentTime)

        return true
    }

    /**
     * 写历史 + 转发（异步）。
     */
    private fun commitToHistoryAndForward(sbn: StatusBarNotification) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                logSbnDetail("黑影 通过", sbn)
                val added = NotificationRepository.addNotification(sbn, this@NotifyRelayNotificationListenerService)
                if (added) {
                    forwardNotificationToRemoteDevices(sbn)
                } else {
                    Logger.i(TAG, "[NotifyListener] 本地已存在该通知，未转发到远程设备: sbnKey=${sbn.key}, pkg=${sbn.packageName}")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "[NotifyListener] addNotification error", e)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Logger.i(TAG, "[NotifyListener] onNotificationPosted called, sbnKey=${sbn.key}, pkg=${sbn.packageName}")
        val isMedia = sbn.notification.category == Notification.CATEGORY_TRANSPORT
        processNotification(sbn)
    }

    private fun forwardNotificationToRemoteDevices(sbn: StatusBarNotification) {
        Logger.i(TAG, "[NotifyListener] forwardNotificationToRemoteDevices called, sbnKey=${sbn.key}, pkg=${sbn.packageName}")
        try {
            val appName = getAppName(sbn.packageName)

            // 使用整合的消息发送工具
            MessageSender.sendNotificationMessage(
                applicationContext,
                sbn.packageName,
                appName,
                getNotificationTitle(sbn),
                getNotificationText(sbn),
                sbn.postTime,
                deviceManager,
            )
        } catch (e: Exception) {
            Logger.e(TAG, "自动转发通知到远程设备失败", e)
        }
    }

    override fun onListenerConnected() {
        Logger.i(TAG, "[NotifyListener] onListenerConnected called")
        super.onListenerConnected()
        // 检查监听服务是否启用（复用 :base 的 ComponentName 精确匹配实现）
        val isEnabled = PermissionHelper.checkNotificationListenerServiceCanStart(applicationContext)
        Logger.i(TAG, "[NotifyListener] Listener enabled: $isEnabled")
        if (!isEnabled) {
            Logger.w(TAG, "[NotifyListener] NotificationListenerService 未被系统启用，无法获取通知！")
        }
        // 启动 MediaSession 监控服务
        mediaSessionMonitorService.startMonitoring()
        // 启动时同步所有活跃通知到历史，后台处理
        val actives = activeNotifications
        if (actives != null) {
            Logger.i(TAG, "[NotifyListener] onListenerConnected: activeNotifications.size=${actives.size}")
            CoroutineScope(Dispatchers.Default).launch {
                for (sbn in actives) {
                    processNotification(sbn, true)
                }
            }
        } else {
            Logger.w(TAG, "[NotifyListener] activeNotifications is null")
        }
        // 启动前台服务，保证后台存活
        startForegroundService()
        // 定时拉取活跃通知，保证后台实时性
        foregroundJob?.cancel()
        foregroundJob =
            CoroutineScope(Dispatchers.Default).launch {
                while (true) {
                    delay(30000)
                    val actives = activeNotifications
                    if (actives != null) {
                        for (sbn in actives) {
                            if (sbn.packageName == applicationContext.packageName) continue
                            processNotification(sbn, true)
                        }
                        // 定期清理过期的缓存，避免内存泄漏
                        cleanupExpiredCacheEntries(System.currentTimeMillis())
                        if (processedNotifications.size > NotificationProcessedCache.CACHE_CLEANUP_THRESHOLD) {
                            Logger.d(TAG, "[NotifyListener] 缓存大小: ${processedNotifications.size}")
                        }
                    } else {
                        Logger.w(TAG, "[NotifyListener] 定时拉取 activeNotifications is null")
                    }
                }
            }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // 释放 WakeLock（避免权限掉落时持有唤醒锁导致耗电）
        releaseWakeLock()
        // 停止 MediaSession 监控服务
        mediaSessionMonitorService.stopMonitoring()

        // 兜底：通知监听权限被系统收回时，若应用仍处于前台，回弹引导页要求重新授权，
        // 避免胶囊歌词/媒体浮窗/转发等功能在权限掉落后静默失效且无入口恢复。
        if (PermissionHelper.isAppInForeground(this) &&
            !PermissionHelper.checkNotificationListenerServiceCanStart(this)
        ) {
            Logger.w(TAG, "[NotifyListener] 权限掉落且应用在前台，回弹引导页重新授权")
            try {
                val intent = Intent(this, GuideActivity::class.java)
                intent.putExtra("reauth", true)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        Logger.i(TAG, "[NotifyListener] onDestroy called")
        // 释放 Wake Lock
        releaseWakeLock()
        // 清空服务实例引用
        instance = null
        super.onDestroy()
        foregroundJob?.cancel()
        sendScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        // 停止 MediaSession 监控服务
        mediaSessionMonitorService.stopMonitoring()
        mediaSessionMonitorService.destroy()
        // 停止设备连接
        try {
            if (this::connectionManager.isInitialized) {
                try {
                    connectionManager.stopDiscovery()
                } catch (e: Exception) {
                    Logger.w(TAG, "[NotifyListener] 停止设备发现失败", e)
                }
            }
        } catch (_: Exception) {
        }
        // 注销网络监听器
        try {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
            networkCallback = null
        } catch (_: Exception) {
        }
    }

    private fun startForegroundService() {
        foregroundController.startForegroundService()
    }

    private fun releaseWakeLock() {
        if (this::foregroundController.isInitialized) {
            foregroundController.releaseWakeLock()
        }
    }

    private fun updateNotification() {
        foregroundController.updateNotification()
    }

    // 保留通知历史，不做移除处理

    internal fun getAppName(packageName: String): String =
        try {
            val pm = applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }

    private fun getNotificationTitle(sbn: StatusBarNotification): String? = NotificationTextReader.getStringCompat(sbn.notification.extras, "android.title")

    private fun getNotificationText(sbn: StatusBarNotification): String? = NotificationTextReader.getNotificationTextWithVerifyCode(sbn)

    internal fun getNotificationKey(
        sbn: StatusBarNotification,
        separator: String = "|",
    ): String = sbn.key ?: (sbn.id.toString() + separator + sbn.packageName)

    internal fun getStorageBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean =
        try {
            StorageManager.getBoolean(applicationContext, key, defaultValue)
        } catch (_: Exception) {
            defaultValue
        }

    private fun logSbnDetail(
        prefix: String,
        sbn: StatusBarNotification,
    ) {
        val title = getNotificationTitle(sbn) ?: ""
        val text = getNotificationText(sbn) ?: ""
        Logger.d(TAG, "$prefix sbnKey=${sbn.key}, pkg=${sbn.packageName}, id=${sbn.id}, title=$title, text=$text")
    }
}
