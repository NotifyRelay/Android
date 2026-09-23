package com.xzyht.notifyrelay.feature.notification.filter

/**
 * 延迟去重缓存（10秒内）- 用于智能去重机制。
 * 持有 title/text/time 三元组，按时间清理过期项并提供最近重复判断。
 */
internal object RemoteFilterDedupCache {
    private val cache = mutableListOf<Triple<String, String, Long>>() // title, text, time

    fun add(
        title: String,
        text: String,
    ) {
        synchronized(cache) {
            cache.add(Triple(title, text, System.currentTimeMillis()))
        }
    }

    fun containsRecent(
        title: String,
        text: String,
    ): Boolean {
        synchronized(cache) {
            cache.removeAll { System.currentTimeMillis() - it.third > 10_000 } // 清理过期缓存
            return cache.any { it.first == title && it.second == text }
        }
    }

    fun clear() {
        synchronized(cache) { cache.clear() }
    }
}
