package com.xzyht.notifyrelay.feature.notification.superisland.cache

import com.xzyht.notifyrelay.feature.notification.superisland.notification.LiveUpdatesIconLoader
import com.xzyht.notifyrelay.feature.notification.superisland.notification.ReplicaIconCache
import com.xzyht.notifyrelay.ui.pages.superisland.SuperIslandImageCache
import github.xzynine.superislandui.common.BitmapUtils
import notifyrelay.shared.log.Logger

/**
 * 超级岛位图与图标缓存的统一回收入口。
 *
 * 覆盖 LiveUpdates 图标缓存、BitmapUtils 位图缓存池、[ReplicaIconCache] 与 [SuperIslandImageCache]。
 */
internal object SuperIslandBitmapCaches {
    private const val TAG = "SuperIslandBitmapCaches"

    /**
     * 清空全部位图与图标缓存。
     *
     * 每一步独立捕获异常，单步失败不影响其余步骤。
     */
    fun clearAll() {
        try {
            LiveUpdatesIconLoader.clearIconCache()
        } catch (e: RuntimeException) {
            Logger.w(TAG, "清理 LiveUpdates 图标缓存失败", e)
        }

        try {
            BitmapUtils.clearCache()
        } catch (e: RuntimeException) {
            Logger.w(TAG, "清理 BitmapUtils 缓存失败", e)
        }

        try {
            ReplicaIconCache.clear()
        } catch (e: RuntimeException) {
            Logger.w(TAG, "清理 ReplicaIconCache 失败", e)
        }

        try {
            SuperIslandImageCache.clear()
        } catch (e: RuntimeException) {
            Logger.w(TAG, "清理 SuperIslandImageCache 失败", e)
        }
    }
}
