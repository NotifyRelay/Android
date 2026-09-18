package com.xzyht.notifyrelay.feature.device.model

import android.content.Context
import com.xzyht.notifyrelay.sync.notification.data.NotificationRecord
import com.xzyht.notifyrelay.sync.notification.data.NotificationRecordDto
import kotlinx.coroutines.runBlocking
import notifyrelay.base.util.Logger

/**
 * 通知历史的持久化写入（plan.md「步骤 4」抽取）。
 *
 * 只负责把内存态写入 Room Store。**不持有任何锁**：调用方 [NotificationRepository.syncToCache]
 * 仍是 `@Synchronized`，本对象在其监视器内被调用，锁粒度与拆分前完全一致
 * （`@Synchronized` + `runBlocking` 的组合不可新增第二把锁，否则引入死锁风险）。
 */
internal object NotificationPersistence {
    /**
     * 将当前设备的通知列表同步到本地缓存。
     * 调用前需已持有 [NotificationRepository] 的监视器。
     */
    fun syncToCache(
        context: Context,
        notifications: List<NotificationRecord>,
        currentDevice: String,
    ) {
        val ctxType = context::class.java.name
        val ctxHash = System.identityHashCode(context)
        Logger.i("NotifyRelay", "[syncToCache] contextType=$ctxType, hash=$ctxHash")
        try {
            val store = NotifyRelayStoreProvider.getInstance(context)
            // 只同步当前设备的通知，避免影响其他设备
            val currentDeviceNotifications = notifications.filter { it.device == currentDevice }
            val entities =
                currentDeviceNotifications.map {
                    NotificationRecordDto(
                        key = it.key,
                        packageName = it.packageName,
                        appName = it.appName,
                        title = it.title,
                        text = it.text,
                        time = it.time,
                        device = it.device,
                    )
                }
            val fileKey = if (currentDevice == "本机") "local" else currentDevice
            // writeAll是suspend函数，需要runBlocking
            runBlocking { store.writeAll(entities, fileKey) }
            Logger.i("回声 NotifyRelay", "写入本地历史 device=$currentDevice, fileKey=$fileKey, size=${entities.size}")
            NotificationMemoryStore.scanDeviceList(context)
        } catch (e: Exception) {
            val device = notifications.firstOrNull()?.device ?: "(unknown)"
            Logger.e("NotifyRelay", "通知保存到缓存失败, contextType=$ctxType, hash=$ctxHash, device=$device, error=${e.message}\n${e.stackTraceToString()}")
        }
    }
}
