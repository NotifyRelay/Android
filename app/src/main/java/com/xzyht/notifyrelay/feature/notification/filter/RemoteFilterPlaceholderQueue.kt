package com.xzyht.notifyrelay.feature.notification.filter

import notifyrelay.base.util.Logger
import notifyrelay.base.util.TextUtils

/**
 * 延迟复刻占位队列（用于锁屏延迟复刻的占位，15s 可被本机入队取消）。
 * 由 BackendRemoteFilter 持引用并响应 [BackendRemoteFilter.onLocalNotificationEnqueued]，
 * 自身不引入去重缓存依赖，命中时通过回调写入去重缓存，保持职责单一。
 */
internal class RemoteFilterPlaceholderQueue {
    data class Placeholder(
        val title: String,
        val text: String,
        val packageName: String,
        val createTime: Long,
        val ttl: Long,
    )

    private val pendingPlaceholders = mutableListOf<Placeholder>()

    /** 添加占位（用于锁屏延迟复刻场景）。 */
    fun add(
        title: String,
        text: String,
        packageName: String,
        ttl: Long = 15_000L,
    ) {
        val ph =
            Placeholder(
                title = title,
                text = text,
                packageName = packageName,
                createTime = System.currentTimeMillis(),
                ttl = ttl,
            )
        synchronized(pendingPlaceholders) {
            pendingPlaceholders.add(ph)
        }
        // Logger.d("智能去重", "添加延迟复刻占位 - 标题:$title, 包名:$packageName, ttl=${ttl}ms")
    }

    /**
     * 移除匹配的占位（通常由本机入队触发）。命中项通过 [onMatched] 回调交由调用方写入去重缓存
     * （默认空实现；仅 [BackendRemoteFilter.onLocalNotificationEnqueued] 需要写缓存，
     * [BackendRemoteFilter.removePlaceholderMatching] 保持基线「只移除不写缓存」语义）。
     * 返回是否移除成功。
     */
    fun removeMatching(
        title: String?,
        text: String?,
        packageName: String,
        onMatched: (Placeholder) -> Unit = {},
    ): Boolean {
        val normalizedTitle = TextUtils.normalizeTitle(title ?: "")
        val pendingText = text ?: ""
        synchronized(pendingPlaceholders) {
            val matches =
                pendingPlaceholders.filter { ph ->
                    TextUtils.normalizeTitle(ph.title) == normalizedTitle && ph.text == pendingText && ph.packageName == packageName
                }
            if (matches.isNotEmpty()) {
                matches.forEach { ph ->
                    try {
                        pendingPlaceholders.remove(ph)
                        onMatched(ph)
                        // Logger.d("智能去重", "占位已取消 - 标题:${ph.title}")
                    } catch (e: Exception) {
                        Logger.e("智能去重", "取消占位失败 - 标题:${ph.title}", e)
                    }
                }
                // Logger.d("智能去重", "移除占位 - 标题:${title}, 包名:$packageName, 数量:${matches.size}")
                return true
            }
        }
        return false
    }

    /** 检查占位是否仍然存在（并清理过期项）。 */
    fun isPresent(
        title: String?,
        text: String?,
        packageName: String,
    ): Boolean {
        val now = System.currentTimeMillis()
        val normalizedTitle = TextUtils.normalizeTitle(title ?: "")
        val pendingText = text ?: ""
        synchronized(pendingPlaceholders) {
            // 清理过期占位
            pendingPlaceholders.removeAll { now - it.createTime > it.ttl }
            return pendingPlaceholders.any { ph ->
                TextUtils.normalizeTitle(ph.title) == normalizedTitle && ph.text == pendingText && ph.packageName == packageName
            }
        }
    }

    /** 清理过期占位（由监控协程周期调用，避免内存泄露）。 */
    fun clearExpired() {
        val now = System.currentTimeMillis()
        synchronized(pendingPlaceholders) {
            pendingPlaceholders.removeAll { now - it.createTime > it.ttl }
        }
    }

    fun clear() {
        synchronized(pendingPlaceholders) { pendingPlaceholders.clear() }
    }
}
