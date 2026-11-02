package com.xzyht.notifyrelay.feature.device.service.state

import android.content.Context
import com.xzyht.notifyrelay.nativecore.device.service.state.OnlineDevicesCache

/**
 * Android 侧在线设备缓存。
 *
 * 在公共端缓存逻辑之上补充动态快捷方式刷新（桌面端无此概念）。
 */
class AndroidOnlineDevicesCache(
    private val context: Context,
) : OnlineDevicesCache() {
    override fun onCacheUpdated() {
        try {
            io.github.miuzarte.scrcpyforandroid.services.DynamicShortcutManager
                .updateShortcuts(context)
        } catch (_: Exception) {
        }
    }
}
