package com.xzyht.notifyrelay.feature.device.model

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.xzyht.notifyrelay.feature.notification.filter.BackendRemoteFilter
import com.xzyht.notifyrelay.feature.notification.filter.RemoteFilterConfig
import com.xzyht.notifyrelay.sync.notification.data.NotificationRecord
import com.xzyht.notifyrelay.sync.notification.data.NotificationRecordDto
import kotlinx.coroutines.runBlocking
import notifyrelay.base.util.Logger
import notifyrelay.data.database.repository.DatabaseRepository

/**
 * 通知历史门面（原 NotificationData.kt）。
 *
 * 职责已按 plan.md「步骤 4」拆分：
 * - 内存态（notifications / currentDevice / deviceList / scanDeviceList）→ [NotificationMemoryStore]
 * - 持久化写入（syncToCache）→ [NotificationPersistence]
 * - 文本/验证码读取 → [NotificationTextReader]
 * - 缓存与老化清理 → [NotificationCacheCleaner]
 *
 * **锁契约（不可变）**：所有对外方法仍在此对象上加 `@Synchronized`，被委托对象**不自持锁**。
 * 拆分前由同一监视器串行保护内存态与 `runBlocking` 写库；拆分后保持一致，
 * 尤其 `addRemoteNotification`（`@JvmStatic`，可能运行在 Rust/JNA 原生线程）的锁粒度与拆分前逐一相同。
 */
object NotificationRepository {
    // 新增：通知历史 StateFlow，UI可订阅
    private val _notificationHistoryFlow = kotlinx.coroutines.flow.MutableStateFlow<List<NotificationRecord>>(emptyList())
    val notificationHistoryFlow: kotlinx.coroutines.flow.StateFlow<List<NotificationRecord>> get() = _notificationHistoryFlow

    // 内存态委托（保持 public API 不变；实际持有者为 NotificationMemoryStore）
    val notifications: SnapshotStateList<NotificationRecord> get() = NotificationMemoryStore.notifications

    var currentDevice: String
        get() = NotificationMemoryStore.currentDevice
        set(value) {
            NotificationMemoryStore.currentDevice = value
        }

    val deviceList: MutableList<String> get() = NotificationMemoryStore.deviceList

    /**
     * 主动刷新指定设备的通知历史并推送到StateFlow
     */
    @Synchronized
    fun notifyHistoryChanged(
        deviceKey: String,
        context: Context,
    ) {
        // 只允许刷新 currentDevice 的内容，禁止外部刷新非 currentDevice
        val realKey = currentDevice
        try {
            val store = NotifyRelayStoreProvider.getInstance(context)
            val history = runBlocking { store.getAll(if (realKey == "本机") "local" else realKey) }
            val mapped =
                history.map {
                    NotificationRecord(
                        key = it.key,
                        packageName = it.packageName,
                        appName = it.appName,
                        title = it.title,
                        text = it.text,
                        time = it.time,
                        device = it.device,
                    )
                }
            _notificationHistoryFlow.value = mapped
            // 同时更新内存列表，确保内存与当前设备同步
            notifications.clear()
            notifications.addAll(mapped)
            // Logger.d("NotifyRelay", "notifyHistoryChanged device=$realKey, 加载数量=${mapped.size}")
        } catch (e: Exception) {
            _notificationHistoryFlow.value = emptyList()
            notifications.clear()
            Logger.e("NotifyRelay", "notifyHistoryChanged 失败", e)
        }
    }

    /**
     * 新增：以远程设备uuid存储转发通知
     */
    @JvmStatic
    fun addRemoteNotification(
        packageName: String,
        appName: String?,
        title: String,
        text: String,
        time: Long,
        device: String,
        context: Context,
    ) {
        val ctxType = context::class.java.name
        val ctxHash = System.identityHashCode(context)
        Logger.i("秩序之光 狂鼠 NotifyRelay", "[addRemoteNotification] contextType=$ctxType, hash=$ctxHash, device=$device")
        if (context !is android.app.Application) {
            Logger.w("秩序之光 狂鼠 NotifyRelay", "[addRemoteNotification] context is not Application: $ctxType, hash=$ctxHash")
        }
        val key = (time.toString() + packageName + device)
        // 使用传入的appName参数
        try {
            val store = NotifyRelayStoreProvider.getInstance(context)
            val fileKey = device // 远程设备uuid
            val oldList = runBlocking { store.getAll(fileKey) }.toMutableList()
            oldList.removeAll { it.key == key }
            // device 字段严格等于 fileKey，保证UI读取时一致
            oldList.add(
                0,
                NotificationRecordDto(
                    key = key,
                    packageName = packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    time = time,
                    device = fileKey,
                ),
            )
            // writeAll是suspend函数，需要runBlocking
            runBlocking { store.writeAll(oldList, fileKey) }
            Logger.i("秩序之光 狂鼠 NotifyRelay", "写入远端历史 device=$device, size=${oldList.size}")

            // 限制每个包名的通知数量为80
            val repository = DatabaseRepository.getInstance(context)
            runBlocking {
                repository.deleteOldestNotificationsByPackageAndDevice(packageName, device, 80)
            }
        } catch (e: Exception) {
            Logger.e("秩序之光 狂鼠 NotifyRelay", "[addRemoteNotification] 写入远程设备json失败: $device, error=${e.message}")
        }
        // 写入后主动推送变更
        notifyHistoryChanged(device, context)
        Logger.i("秩序之光 狂鼠 NotifyRelay", "[addRemoteNotification] after sync (no global add), device=$device")
    }

    /**
     * 新增通知到历史记录（支持监听服务调用）
     * @return true 表示本地历史中原本不存在该通知（即为新增）
     */
    @Synchronized
    fun addNotification(
        sbn: StatusBarNotification,
        context: Context,
    ): Boolean {
        val notification = sbn.notification
        val time = sbn.postTime

        // title 读取使用严格版（不吞异常），保持拆分前内联版的异常语义（见 NotificationTextReader.getStringCompatStrict）
        val title = NotificationTextReader.getStringCompatStrict(notification.extras, Notification.EXTRA_TITLE)
        // 使用 getNotificationTextWithVerifyCode 读取文本，优先读取 verify_code 字段
        val text = NotificationTextReader.getNotificationTextWithVerifyCode(sbn)
        val packageName = sbn.packageName
        val device = "本机"
        // 本地通知的 key 也需要包含设备信息，确保不同设备的相同通知不会冲突
        val key = ((sbn.key ?: (sbn.id.toString() + sbn.packageName)) + "_" + time.toString()) + "_" + device
        var appName: String? = null
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appName = pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            appName = packageName
        }
        val record =
            NotificationRecord(
                key = key,
                packageName = packageName,
                appName = appName,
                title = title, // 这里始终用实际通知标题
                text = text,
                time = time,
                device = device,
            )
        // 改进判重逻辑：对于活跃通知，允许时间戳有一定差异（5秒内），避免因时间戳微差导致重复
        // 注意：这里不删除历史记录，只是不添加重复的通知
        var existed = false
        val timeTolerance = 5000L // 5秒容差
        notifications.forEach {
            if (it.packageName == packageName &&
                (it.title ?: "") == (title ?: "") &&
                (it.text ?: "") == (text ?: "")
            ) {
                // 时间戳在容差范围内认为相同
                if (Math.abs(it.time - time) <= timeTolerance) {
                    existed = true
                    Logger.i("回声 NotifyRelay", "[判重] 发现重复通知，不添加到历史: key=${it.key}, pkg=${it.packageName}, title=${it.title}, text=${it.text}, time差=${Math.abs(it.time - time)}ms")
                }
            }
        }

        // 只有在没有重复时才添加新通知
        if (!existed) {
            notifications.removeAll {
                it.key == key ||
                    (
                        it.packageName == packageName &&
                            (it.title ?: "") == (title ?: "") &&
                            (it.text ?: "") == (text ?: "") &&
                            Math.abs(it.time - time) <= timeTolerance
                    )
            }
            notifications.add(0, record)
            syncToCache(context)
            // 被动去重：仅在智能去重开启时，通知 BackendRemoteFilter 检查是否命中可撤回队列并撤回复刻通知
            try {
                if (RemoteFilterConfig.enableDeduplication) {
                    BackendRemoteFilter
                        .onLocalNotificationEnqueued(title, text, packageName, time, context)
                } else {
                    // Logger.d("NotifyRelay", "智能去重已关闭，跳过被动去重推送")
                }
            } catch (e: Exception) {
                Logger.e("NotifyRelay", "调用 onLocalNotificationEnqueued 失败", e)
            }

            // 限制每个包名的通知数量为80
            val repository = DatabaseRepository.getInstance(context)
            runBlocking {
                repository.deleteOldestNotificationsByPackageAndDevice(packageName, device, 80)
            }
        }

        notifyHistoryChanged(device, context)
        return !existed
    }

    // 扫描设备列表（委托 NotificationMemoryStore，保持原调用点不变）
    fun scanDeviceList(context: Context) {
        NotificationMemoryStore.scanDeviceList(context)
    }

    private var hasCleanedUpOldNotifications = false

    @Synchronized
    fun init(context: Context) {
        try {
            scanDeviceList(context)
            NotifyRelayStoreProvider.getInstance(context)
            // 主动加载本地历史到内存，保证判重有效
            val store2 = NotifyRelayStoreProvider.getInstance(context)
            val localList =
                runBlocking {
                    store2.readAll("本机").map {
                        NotificationRecord(
                            key = it.key,
                            packageName = it.packageName,
                            appName = it.appName,
                            title = it.title,
                            text = it.text,
                            time = it.time,
                            device = it.device,
                        )
                    }
                }
            notifications.clear()
            notifications.addAll(localList)

            // 清理历史通知，确保每个包名的通知数量不超过80条
            if (!hasCleanedUpOldNotifications) {
                runBlocking {
                    cleanupOldNotifications(context)
                }
                hasCleanedUpOldNotifications = true
            }
        } catch (e: Exception) {
            notifications.clear()
        }
    }

    /**
     * 移除指定 key 的通知
     */
    @Synchronized
    fun removeNotification(
        key: String,
        context: Context,
    ) {
        // Logger.d("NotifyRelay", "开始删除通知 key=$key")

        // 查找要删除的通知，检查其设备类型
        val notificationToRemove = notifications.find { it.key == key && it.device == currentDevice }
        val isLocalDevice = notificationToRemove?.device == "本机"

        // 只删除当前设备的通知
        notifications.removeAll { it.key == key && it.device == currentDevice }

        // 调用Room数据库的删除方法
        val store = NotifyRelayStoreProvider.getInstance(context)
        runBlocking {
            store.deleteByKey(key, currentDevice)
        }

        // 仅在本机设备时清理processedNotifications缓存
        if (isLocalDevice) {
            clearProcessedCache(setOf(key))
        }

        // 通知历史变更，UI会自动刷新
        // 注意：notifyHistoryChanged会重新从数据库加载数据，所以不需要单独调用syncToCache
        notifyHistoryChanged(currentDevice, context)
    }

    /**
     * 移除指定包名的所有通知（分组删除）
     */
    @Synchronized
    fun removeNotificationsByPackage(
        packageName: String,
        context: Context,
    ) {
        // 收集要清除的通知key，用于清理缓存
        val notificationsToRemove = notifications.filter { it.packageName == packageName && it.device == currentDevice }
        val keysToRemove = notificationsToRemove.map { it.key }.toSet()

        // 检查是否有本机设备的通知
        val hasLocalNotifications = notificationsToRemove.any { it.device == "本机" }
        val localKeysToClear = notificationsToRemove.filter { it.device == "本机" }.map { it.key }.toSet()

        // 移除内存中的通知
        notifications.removeAll { it.packageName == packageName && it.device == currentDevice }

        // 使用新添加的高效方法，直接从数据库中删除指定包名和设备的所有通知
        val store = NotifyRelayStoreProvider.getInstance(context)
        runBlocking {
            store.deleteByPackageAndDevice(packageName, currentDevice)
        }

        // 仅清理本机设备的缓存
        if (hasLocalNotifications) {
            clearProcessedCache(localKeysToClear)
        }

        // 通知历史变更，UI会自动刷新
        notifyHistoryChanged(currentDevice, context)
    }

    /**
     * 清除指定设备的通知历史
     */
    @Synchronized
    fun clearDeviceHistory(
        device: String,
        context: Context,
    ) {
        // 只清除当前设备的历史
        val deviceToClear = currentDevice

        // 收集要清除的通知key，用于清理缓存
        val keysToClear = notifications.filter { it.device == deviceToClear }.map { it.key }.toSet()
        notifications.removeAll { it.device == deviceToClear }

        // 调用Room数据库的清除方法
        val store = NotifyRelayStoreProvider.getInstance(context)
        runBlocking {
            store.clearByDevice(deviceToClear)
        }

        // 仅在本机设备时清理processedNotifications缓存（非本机设备没有缓存）
        if (deviceToClear == "本机") {
            // 对于本机设备，直接清除全部缓存（处理遗留问题）
            clearProcessedCacheAll()
        } else {
            // 对于非本机设备，仅清理对应的key
            clearProcessedCache(keysToClear)
        }

        // 写入后主动推送变更
        // 注意：notifyHistoryChanged会重新从数据库加载数据，所以不需要单独调用syncToCache
        notifyHistoryChanged(deviceToClear, context)
    }

    /**
     * 将当前通知列表同步到本地缓存
     */
    @Synchronized
    internal fun syncToCache(context: Context) {
        NotificationPersistence.syncToCache(context, notifications, currentDevice)
    }

    /**
     * 获取指定设备的通知列表
     */
    @Synchronized
    fun getNotificationsByDevice(device: String): List<NotificationRecord> = NotificationMemoryStore.getNotificationsByDevice(device)

    // 缓存清理回调（委托给 NotificationCacheCleaner）
    fun registerCacheCleaner(cleaner: (Set<String>) -> Unit) {
        NotificationCacheCleaner.registerCacheCleaner(cleaner)
    }

    private fun clearProcessedCache(notificationKeys: Set<String>) {
        NotificationCacheCleaner.clearProcessedCache(notificationKeys)
    }

    private fun clearProcessedCacheAll() {
        NotificationCacheCleaner.clearProcessedCacheAll()
    }

    private suspend fun cleanupOldNotifications(context: Context) {
        NotificationCacheCleaner.cleanupOldNotifications(context, deviceList)
    }
}
