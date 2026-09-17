package com.xzyht.notifyrelay.feature.notification.superisland.notification

import android.graphics.drawable.Icon
import java.util.concurrent.ConcurrentHashMap

/**
 * 共享的小图标缓存。
 *
 * 由 [ReplicaScrollUpdater]（滚动更新恢复图标）与 [ReplicaSmallIconInjector]（注入时缓存）
 * 共用同一份缓存，避免各建一份导致缓存泄漏或图标丢失。
 */
internal object ReplicaIconCache {
    // 缓存已注入的小图标，供滚动更新时复用（避免丢失实际意义图标）
    private val cachedSmallIcons = ConcurrentHashMap<String, Icon>()

    fun get(key: String): Icon? = cachedSmallIcons[key]

    fun put(
        key: String,
        icon: Icon,
    ) {
        cachedSmallIcons[key] = icon
    }

    fun remove(key: String) {
        cachedSmallIcons.remove(key)
    }

    fun clear() {
        cachedSmallIcons.clear()
    }
}
