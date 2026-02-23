package com.xzyht.notifyrelay.servers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.xzyht.notifyrelay.R
import com.xzyht.notifyrelay.sync.MessageSender
import com.xzyht.notifyrelay.servers.clipboard.ClipboardSyncManager
import com.xzyht.notifyrelay.servers.clipboard.ClipboardSyncReceiver
import com.xzyht.notifyrelay.feature.device.model.NotificationRepository
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
import com.xzyht.notifyrelay.feature.notification.backend.BackendLocalFilter
import com.xzyht.notifyrelay.feature.notification.superisland.FloatingReplicaManager
import com.xzyht.notifyrelay.feature.notification.superisland.common.SuperIslandManager
import com.xzyht.notifyrelay.feature.notification.superisland.common.SuperIslandProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import notifyrelay.base.util.Logger
import notifyrelay.core.util.DataUrlUtils
import notifyrelay.data.StorageManager
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

class NotifyRelayNotificationListenerService : NotificationListenerService() {
    companion object {
        private const val TAG = "NotifyRelayNotificationListenerService"
        private const val MAX_CACHE_SIZE = 2000
        private const val CACHE_CLEANUP_THRESHOLD = 1500
        private const val CACHE_ENTRY_TTL = 24 * 60 * 60 * 1000L // 24小时TTL
        // 最新的媒体播放通知（用于被外部工具查询并触发其 action）
        @Volatile
        var latestMediaSbn: StatusBarNotification? = null
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // 只补发本应用的前台服务通知（必须channelId和id都匹配）
        if (sbn.packageName == applicationContext.packageName
            && sbn.notification.channelId == CHANNEL_ID
            && sbn.id == NOTIFY_ID) {
            Logger.w(TAG, "前台服务通知被移除，自动补发！")
            // 立即补发本服务前台通知
            startForegroundService()
        } else if (sbn.packageName == applicationContext.packageName) {
            // 检查是否为超级岛相关通知（包括普通超级岛和焦点歌词）
            val channelId = sbn.notification.channelId
            if (channelId == "super_island_replica" || channelId == "channel_id_focusNotifLyrics") {
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
                        deviceManager
                    )
                    // 清理媒体状态缓存
                    mediaPlayStateByKey.remove(notificationKey)
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
                    val pair = superIslandFeatureByKey.remove(notificationKey)
                    if (pair != null) {
                        val deviceManager = this.deviceManager
                        val (superPkg, featureId) = pair
                        MessageSender.sendSuperIslandEnd(
                            applicationContext,
                            superPkg,
                            try { applicationContext.packageName } catch (_: Exception) { null },
                            System.currentTimeMillis(),
                            try { SuperIslandManager.extractSuperIslandData(sbn, applicationContext)?.paramV2Raw } catch (_: Exception) { null },
                            getNotificationTitle(sbn),
                            getNotificationText(sbn),
                            deviceManager,
                            featureIdOverride = featureId
                        )
                    }
                } catch (_: Exception) {}
            }
        }
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        Logger.i(TAG, "[NotifyListener] onTaskRemoved called, rootIntent=$rootIntent")
        super.onTaskRemoved(rootIntent)
        // 重新启动服务，防止被系统杀死
        val restartIntent =
            Intent(applicationContext, NotifyRelayNotificationListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(restartIntent)
        } else {
            applicationContext.startService(restartIntent)
        }
    }
    override fun onCreate() {
        Logger.i(TAG, "[NotifyListener] onCreate called")
        // 注册缓存清理器
        NotificationRepository.registerCacheCleaner { keysToRemove ->
            if (keysToRemove.isEmpty()) {
                // 空集合表示清除全部缓存
                val beforeSize = processedNotifications.size
                processedNotifications.clear()
                Logger.i(TAG, "[NotifyListener] 清理全部processedNotifications缓存，清除前: $beforeSize 个条目")
            } else {
                // 清除指定的缓存项
                val beforeSize = processedNotifications.size
                processedNotifications.keys.removeAll(keysToRemove)
                val afterSize = processedNotifications.size
                Logger.i(TAG, "[NotifyListener] 清理processedNotifications缓存，清除前: $beforeSize，清除后: $afterSize，移除 ${keysToRemove.size} 个条目")
            }
        }
        // 确保本地历史缓存已加载，避免首次拉取时判重失效
        NotificationRepository.init(applicationContext)
        // 初始化设备连接管理器并启动发现
        connectionManager = DeviceConnectionManagerSingleton.getDeviceManager(applicationContext)
        try {
            val discoveryField = connectionManager.javaClass.getDeclaredField("discoveryManager")
            discoveryField.isAccessible = true
            val discovery = discoveryField.get(connectionManager)
            val startMethod = discovery.javaClass.getDeclaredMethod("startDiscovery")
            startMethod.isAccessible = true
            startMethod.invoke(discovery)
        } catch (_: Exception) {}

        // 监听设备状态变化，更新通知
        CoroutineScope(Dispatchers.Default).launch {
            connectionManager.devices.collect { deviceMap ->
                // 设备状态发生变化时更新通知
                updateNotification()
            }
        }

        // 监听网络状态变化，更新通知
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNotification()
            }

            override fun onLost(network: Network) {
                updateNotification()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
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
    private val CHANNEL_ID = "notifyrelay_foreground"
    private val NOTIFY_ID = 1001

    // 设备连接管理器
    private lateinit var connectionManager: DeviceConnectionManager
    private val deviceManager by lazy { DeviceConnectionManagerSingleton.getDeviceManager(applicationContext) }

    // 网络监听器
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // 新增：已处理通知缓存，避免重复处理 (改进版：带时间戳的LRU缓存)
    private val processedNotifications = ConcurrentHashMap<String, Long>()
    // 记录本机转发过的超级岛特征ID，用于在移除时发送终止包
    private val superIslandFeatureByKey = ConcurrentHashMap<String, Pair<String, String>>() // sbnKey -> (superPkg, featureId)

    // 媒体播放通知状态管理：使用sbn.key作为会话键，跟踪每个媒体通知的状态
    private val mediaPlayStateByKey = ConcurrentHashMap<String, MediaPlayState>()

    // 媒体播放状态数据类
    data class MediaPlayState(
        val title: String,
        val text: String,
        val packageName: String,
        val postTime: Long,
        val coverUrl: String? = null,
        val sentTime: Long = System.currentTimeMillis() // 添加发送时间戳
    )

    // 使用通用工具将 Drawable 转换为 Bitmap（参照项目中其他模块的实现）



    /**
     * 处理媒体播放通知
     */
    private fun processMediaNotification(sbn: StatusBarNotification) {
        // 更新全局持有的最新媒体通知，方便外部通过工具类触发操作
        try {
            latestMediaSbn = sbn
        } catch (_: Exception) {}
        val sbnKey = getNotificationKey(sbn)
        val title = getNotificationTitle(sbn) ?: ""
        val text = getNotificationText(sbn) ?: ""

        // 初始化封面URL变量
        var coverUrl: String? = null

        // 获取音乐封面图标（无论胶囊歌词是否开启都提取）
        try {
            // 尝试从通知的大图中获取封面
            val largeIcon = sbn.notification.getLargeIcon()
            if (largeIcon != null) {
                // 将Drawable转换为Bitmap
                val drawable = largeIcon.loadDrawable(applicationContext)
                if (drawable != null) {
                    val bitmap = DataUrlUtils.drawableToBitmap(drawable)
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                    val bytes = stream.toByteArray()
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    coverUrl = "data:image/jpeg;base64,$base64"
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "获取音乐封面失败", e)
        }

        // 检查胶囊歌词开关状态
        val capsuleLyricsEnabled = getStorageBoolean("capsule_lyrics_enabled", false)

        // 如果胶囊歌词开关开启，直接在本机内生成浮窗和通知
        if (capsuleLyricsEnabled) {
            try {
                Logger.i(TAG, "胶囊歌词开关开启，在本机内生成浮窗和通知: title='$title', text='$text'")
                
                // 构建图片映射
                val picMap = mutableMapOf<String, String>()
                if (!coverUrl.isNullOrBlank()) {
                    // 添加专辑图，同时添加应用图标键以确保图片能够正确传递
                    picMap["miui.focus.pic_cover"] = coverUrl
                    picMap["miui.focus.pic_app_icon"] = coverUrl
                }
                
                // 创建媒体类型的paramV2Raw，确保触发超长后使用图标文本的功能
                val paramV2Raw = "{\"business\":\"media\",\"protocol\":1,\"scene\":\"music\",\"ticker\":\"$text\",\"content\":\"$title\",\"enableFloat\":false,\"updatable\":true,\"reopen\":\"close\",\"localTransmit\":true}"
                
                // 调用 FloatingReplicaManager.showFloating 生成浮窗和通知
                // 无论播放状态如何，都保持浮窗显示，避免UI变化频繁
                FloatingReplicaManager.showFloating(
                    context = applicationContext,
                    sourceId = sbnKey,
                    title = title ,  
                    text = text,  
                    paramV2Raw = paramV2Raw,  // 添加媒体类型的paramV2Raw
                    picMap = picMap,
                    appName = sbn.packageName
                )
            } catch (e: Exception) {
                Logger.e(TAG, "在本机内生成浮窗和通知失败", e)
            }
        }

        // 检查状态是否变化，只在内容变化时发送
        val currentState = MediaPlayState(title, text, sbn.packageName, sbn.postTime, coverUrl)
        val lastState = mediaPlayStateByKey[sbnKey]

        // 发送条件：状态变化 或 距离上次发送超过15秒
        val now = System.currentTimeMillis()
        val sendRequired = lastState == null ||
                          lastState.title != currentState.title ||
                          lastState.text != currentState.text ||
                          lastState.coverUrl != currentState.coverUrl ||
                          (now - lastState.sentTime > 15 * 1000) // 超过15秒未发送

        if (sendRequired) {
            // 状态变化或超时，发送消息

            try {
                val appName = getAppName(sbn.packageName)

                // 使用专门的协议前缀标记媒体通知,使用报文头（"type":"MEDIA_PLAY"）判断媒体消息
                MessageSender.sendMediaPlayNotification(
                    applicationContext,
                    sbn.packageName,
                    appName,
                    title,
                    text,
                    coverUrl,
                    sbn.postTime,
                    deviceManager
                )

                // 更新状态缓存，包含发送时间
                mediaPlayStateByKey[sbnKey] = currentState.copy(sentTime = now)
            } catch (e: Exception) {
                Logger.e(TAG, "发送媒体播放消息失败", e)
            }
        }
    }

    private fun cleanupExpiredCacheEntries(currentTime: Long) {
        if (processedNotifications.size <= CACHE_CLEANUP_THRESHOLD) return

        val expiredKeys = processedNotifications.filter { (_, timestamp) ->
            currentTime - timestamp > CACHE_ENTRY_TTL
        }.keys

        if (expiredKeys.isNotEmpty()) {
            processedNotifications.keys.removeAll(expiredKeys)
            Logger.i(TAG, "[NotifyListener] 清理过期缓存条目: ${expiredKeys.size} 个")
        }

        // 如果仍然超过最大大小，进行LRU清理
        if (processedNotifications.size > MAX_CACHE_SIZE) {
            val entriesToRemove = processedNotifications.size - MAX_CACHE_SIZE
            val sortedByTime = processedNotifications.entries.sortedBy { it.value }
            val keysToRemove = sortedByTime.take(entriesToRemove).map { it.key }
            processedNotifications.keys.removeAll(keysToRemove)
            Logger.i(TAG, "[NotifyListener] LRU清理缓存条目: ${keysToRemove.size} 个")
        }
    }

    private fun processNotification(sbn: StatusBarNotification, checkProcessed: Boolean = false) {
        // 读取超级岛设置开关，决定是否按超级岛专用逻辑处理
        val superIslandEnabled = getStorageBoolean("superisland_enabled", true)

        // 检查是否为媒体播放通知
        val isMediaNotification = sbn.notification.category == Notification.CATEGORY_TRANSPORT
        if (isMediaNotification) {
            // 媒体播放消息，单独处理
            processMediaNotification(sbn)
            return
        }

        // 在本机本地过滤前，尝试读取超级岛信息并单独转发
        // 当开关开启且检测到超级岛数据时，只发送超级岛分支，不再走普通通知转发
        val superIslandHandledAndStop: Boolean = if (superIslandEnabled) {
            try {
                val superData = SuperIslandManager.extractSuperIslandData(sbn, applicationContext)
                if (superData != null) {
                    Logger.i(TAG, "超级岛: 检测到超级岛数据，准备转发，pkg=${superData.sourcePackage}, title=${superData.title}")

                    // 过滤本应用的超级岛通知，不进行转发
                    if (sbn.packageName != applicationContext.packageName) {
                        try {
                            val deviceManager = this.deviceManager
                            // 不再使用包名前缀标记；通过通道头 DATA_SUPERISLAND 区分超级岛
                            val superPkg = superData.sourcePackage ?: "unknown"
                            // 严格以通知 sbn.key 作为会话键：一条系统通知只对应一座"岛"
                            val sbnInstanceId = getNotificationKey(sbn, "")
                            // 优先复用历史特征ID，避免因字段轻微变化导致"不同岛"的错判
                            val oldId = try { superIslandFeatureByKey[sbnInstanceId]?.second } catch (_: Exception) { null }
                            val computedId = SuperIslandProtocol.computeFeatureId(
                                superPkg,
                                superData.paramV2Raw,
                                superData.title,
                                superData.text,
                                sbnInstanceId
                            )
                            val featureId = oldId ?: computedId
                            // 初次出现时登记；后续保持不变
                            try { if (oldId == null) superIslandFeatureByKey[sbnInstanceId] = superPkg to featureId } catch (_: Exception) {}
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
                                featureIdOverride = featureId
                            )
                        } catch (e: Exception) {
                            Logger.w(TAG, "超级岛: 转发超级岛数据失败: ${e.message}")
                        }
                    }
                    // 已按超级岛分支处理，本条不再继续普通转发
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }

        if (superIslandHandledAndStop) {
            // 超级岛分支已完成，只保留本机历史，不再转发普通通知
            logSbnDetail("超级岛: 已按超级岛分支处理，跳过普通转发", sbn)
            return
        }

        if (!BackendLocalFilter.shouldForward(sbn, applicationContext, checkProcessed)) {
            logSbnDetail("法鸡-黑影 被过滤", sbn)
            return
        }
        val notificationKey = sbn.key ?: (sbn.id.toString() + sbn.packageName)
        val currentTime = System.currentTimeMillis()

        // 检查缓存和TTL
        if (checkProcessed) {
            val lastProcessedTime = processedNotifications[notificationKey]
            if (lastProcessedTime != null) {
                // 检查是否过期
                if (currentTime - lastProcessedTime < CACHE_ENTRY_TTL) {
                    return
                } else {
                    // 过期条目，移除
                    processedNotifications.remove(notificationKey)
                }
            }
        }

        // 清理过期缓存条目
        cleanupExpiredCacheEntries(currentTime)

        // 更新缓存
        processedNotifications[notificationKey] = currentTime

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
                deviceManager
            )
        } catch (e: Exception) {
            Logger.e(TAG, "自动转发通知到远程设备失败", e)
        }
    }


    override fun onListenerConnected() {
        Logger.i(TAG, "[NotifyListener] onListenerConnected called")
        super.onListenerConnected()
        // 检查监听服务是否启用
        val enabledListeners = Settings.Secure.getString(
            applicationContext.contentResolver,
            "enabled_notification_listeners"
        )
        val isEnabled = enabledListeners?.contains(applicationContext.packageName) == true
        Logger.i(TAG, "[NotifyListener] Listener enabled: $isEnabled, enabledListeners=$enabledListeners")
        if (!isEnabled) {
            Logger.w(TAG, "[NotifyListener] NotificationListenerService 未被系统启用，无法获取通知！")
        }
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
        foregroundJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                delay(5000)
                val actives = activeNotifications
                if (actives != null) {

                    for (sbn in actives) {
                        if (sbn.packageName == applicationContext.packageName) continue
                        processNotification(sbn, true)
                    }
                    // 定期清理过期的缓存，避免内存泄漏
                    cleanupExpiredCacheEntries(System.currentTimeMillis())
                    if (processedNotifications.size > CACHE_CLEANUP_THRESHOLD) {
                        Logger.d(TAG, "[NotifyListener] 缓存大小: ${processedNotifications.size}")
                    }
                } else {
                    Logger.w(TAG, "[NotifyListener] 定时拉取 activeNotifications is null")
                }
            }
        }
    }

    override fun onDestroy() {
        Logger.i(TAG, "[NotifyListener] onDestroy called")
        super.onDestroy()
        foregroundJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        // 停止设备连接
        try {
            if (this::connectionManager.isInitialized) {
                try {
                    val discoveryField = connectionManager.javaClass.getDeclaredField("discoveryManager")
                    discoveryField.isAccessible = true
                    val discovery = discoveryField.get(connectionManager)
                    val stopMethod = discovery.javaClass.getDeclaredMethod("stopDiscovery")
                    stopMethod.isAccessible = true
                    stopMethod.invoke(discovery)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        // 注销网络监听器
        try {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
            networkCallback = null
        } catch (_: Exception) {}
    }

    private fun startForegroundService() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "通知转发后台服务",
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification = buildNotification()
        startForeground(NOTIFY_ID, notification)
    }

    private fun buildNotification(): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("通知监听/转发中")
            .setContentText(getNotificationText())
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // 检查剪贴板同步状态，为通知主体添加点击事件
        try {
            val accessibilityEnabled = ClipboardSyncManager.isAccessibilityServiceEnabled(this)

            // 为通知主体添加点击事件，实现剪贴板同步功能
            val syncIntent = Intent(this, ClipboardSyncReceiver::class.java).apply {
                action = ClipboardSyncReceiver.Companion.ACTION_MANUAL_SYNC
            }
            val syncPendingIntent = PendingIntent.getBroadcast(
                this, 0, syncIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(syncPendingIntent)
        } catch (e: Exception) {
            Logger.w(TAG, "添加剪贴板点击事件失败", e)
        }

        return builder.build()
    }

    private fun getNotificationText(): String {
        // 使用 DeviceConnectionManager 提供的线程安全方法获取在线且已认证的设备数量
        val onlineDevices = try { connectionManager.getAuthenticatedOnlineCount() } catch (_: Exception) { 0 }
        val accessibilityEnabled = try { ClipboardSyncManager.isAccessibilityServiceEnabled(this) } catch (_: Exception) { false }
        //Logger.d(TAG, "getNotificationText: authenticatedOnlineCount=$onlineDevices")

        // 优先显示设备连接数，如果有设备连接
        if (onlineDevices > 0) {
            return if (!accessibilityEnabled) {
                "当前${onlineDevices}台设备已连接，点击以同步剪贴板"
            } else {
                "当前${onlineDevices}台设备已连接"
            }
        }

        // 没有设备连接时，显示网络状态
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isEthernet = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val isWifiDirect = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_WIFI_P2P) == true

        // 如果不是WiFi、以太网或WLAN直连，则认为是移动数据等非局域网
        val baseText = if (!isWifi && !isEthernet && !isWifiDirect) {
            "非局域网连接"
        } else {
            "无设备在线"
        }

        // 无障碍服务未启用时，添加点击提示
        return if (!accessibilityEnabled) {
            "$baseText，点击通知同步剪贴板"
        } else {
            baseText
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildNotification()
        manager.notify(NOTIFY_ID, notification)
    }

    // 保留通知历史，不做移除处理

    private fun getAppName(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun getNotificationTitle(sbn: StatusBarNotification): String? {
        return NotificationRepository.getStringCompat(sbn.notification.extras, "android.title")
    }

    private fun getNotificationText(sbn: StatusBarNotification): String? {
        return NotificationRepository.getStringCompat(sbn.notification.extras, "android.text")
    }

    private fun getNotificationKey(sbn: StatusBarNotification, separator: String = "|"): String {
        return sbn.key ?: (sbn.id.toString() + separator + sbn.packageName)
    }

    private fun getStorageBoolean(key: String, defaultValue: Boolean): Boolean {
        return try {
            StorageManager.getBoolean(applicationContext, key, defaultValue)
        } catch (_: Exception) {
            defaultValue
        }
    }

    private inline fun safeSend(tag: String, crossinline block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Logger.e(TAG, "发送消息失败", e)
        }
    }

    private fun logSbnDetail(prefix: String, sbn: StatusBarNotification) {
        val title = getNotificationTitle(sbn) ?: ""
        val text = getNotificationText(sbn) ?: ""
        Logger.d(TAG, "$prefix sbnKey=${sbn.key}, pkg=${sbn.packageName}, id=${sbn.id}, title=$title, text=$text")
    }
}
