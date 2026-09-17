package com.xzyht.notifyrelay.feature.notification.service

import notifyrelay.base.util.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * 已处理通知去重缓存（带时间戳的 LRU 缓存）。
 *
 * 被三处并发访问，且必须共享**同一个**实例：
 * - 监听线程：[NotifyRelayNotificationListenerService.onNotificationPosted]
 * - 30s 轮询协程：[NotifyRelayNotificationListenerService.onListenerConnected]
 * - `NotificationRepository.registerCacheCleaner` 回调：[NotifyRelayNotificationListenerService.onCreate]
 */
internal class NotificationProcessedCache {
    companion object {
        private const val TAG = "NotifyRelayNotificationListenerService"
        private const val MAX_CACHE_SIZE = 2000
        const val CACHE_CLEANUP_THRESHOLD = 1500

        // 24小时TTL
        private const val CACHE_ENTRY_TTL = 24 * 60 * 60 * 1000L
    }

    // 改进版：带时间戳的LRU缓存
    private val processedNotifications = ConcurrentHashMap<String, Long>()

    val size: Int get() = processedNotifications.size

    /** 指定键是否已处理且未过期（命中过期条目时会顺带移除）。 */
    fun isRecentlyProcessed(
        notificationKey: String,
        currentTime: Long,
    ): Boolean {
        val lastProcessedTime = processedNotifications[notificationKey] ?: return false
        // 检查是否过期
        if (currentTime - lastProcessedTime < CACHE_ENTRY_TTL) {
            return true
        }
        // 过期条目，移除
        processedNotifications.remove(notificationKey)
        return false
    }

    /** 记录一次处理时间。 */
    fun markProcessed(
        notificationKey: String,
        currentTime: Long,
    ) {
        processedNotifications[notificationKey] = currentTime
    }

    /** 从缓存中移除单个条目（通知被移除时调用）。 */
    fun remove(notificationKey: String) {
        processedNotifications.remove(notificationKey)
    }

    /** 清除全部缓存（空集合表示清除全部）。 */
    fun clear() {
        val beforeSize = processedNotifications.size
        processedNotifications.clear()
        Logger.i(TAG, "[NotifyListener] 清理全部processedNotifications缓存，清除前: $beforeSize 个条目")
    }

    /** 清除指定缓存项。 */
    fun removeAll(keysToRemove: Set<String>) {
        val beforeSize = processedNotifications.size
        processedNotifications.keys.removeAll(keysToRemove)
        val afterSize = processedNotifications.size
        Logger.i(TAG, "[NotifyListener] 清理processedNotifications缓存，清除前: $beforeSize，清除后: $afterSize，移除 ${keysToRemove.size} 个条目")
    }

    /** 清理过期条目；仍超上限时按时间做 LRU 清理。 */
    fun cleanupExpiredEntries(currentTime: Long) {
        if (processedNotifications.size <= CACHE_CLEANUP_THRESHOLD) return

        val expiredKeys =
            processedNotifications
                .filter { (_, timestamp) ->
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
}
