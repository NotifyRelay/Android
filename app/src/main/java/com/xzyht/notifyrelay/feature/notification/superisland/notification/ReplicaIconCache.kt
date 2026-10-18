package com.xzyht.notifyrelay.feature.notification.superisland.notification

import android.graphics.drawable.Icon

/**
 * 共享的小图标缓存。
 *
 * 由 [ReplicaScrollUpdater]（滚动更新恢复图标）与 [ReplicaSmallIconInjector]（注入时缓存）
 * 共用同一份缓存，避免各建一份导致缓存泄漏或图标丢失。
 */
internal object ReplicaIconCache {
    // 缓存条目上限，超出后按访问顺序淘汰最久未使用的条目
    private const val MAX_CACHE_SIZE = 32

    // 缓存已注入的小图标，供滚动更新时复用（避免丢失实际意义图标）
    private val cachedSmallIcons =
        object : LinkedHashMap<String, Icon>(MAX_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Icon>?): Boolean = size > MAX_CACHE_SIZE
        }

    @Synchronized
    fun get(key: String): Icon? = cachedSmallIcons[key]

    @Synchronized
    fun put(
        key: String,
        icon: Icon,
    ) {
        cachedSmallIcons[key] = icon
    }

    @Synchronized
    fun remove(key: String) {
        cachedSmallIcons.remove(key)
    }

    @Synchronized
    fun clear() {
        cachedSmallIcons.clear()
    }
}
