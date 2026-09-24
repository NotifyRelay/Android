package com.xzyht.notifyrelay.feature.notification.superisland.store

import github.xzynine.superislandui.diff.DiffSystem
import java.util.concurrent.ConcurrentHashMap

/**
 * 接收端超级岛远端状态存储与差异合并。
 * key使用 sourceId（通常为 "superisland:pkg|featureId"）。
 */
object SuperIslandRemoteStore {
    private val store = ConcurrentHashMap<String, DiffSystem.State>()

    /**
     * 清空所有远端状态（供公平运行内存回调使用）。
     */
    @Synchronized
    fun clear() {
        store.clear()
    }

    @Synchronized
    fun applyIncoming(
        sourceId: String,
        title: String?,
        text: String?,
        paramV2Raw: String?,
        pics: Map<String, String>,
    ): DiffSystem.State? {
        val state = DiffSystem.State(title, text, paramV2Raw, pics.toMutableMap())
        store[sourceId] = state
        return state
    }

    /**
     * 根据 deviceUuid 和 mappedPkg 前缀寻找并移除匹配的 sourceId 条目，返回被移除的 sourceId 列表。
     * 用于在接收到结束包但 featureId 无法可靠重算时，清理存储并告知上层进行浮窗关闭。
     */
    @Synchronized
    fun removeByDeviceAndPkgPrefix(
        deviceUuid: String,
        mappedPkg: String,
    ): List<String> =
        try {
            val prefix = listOf(deviceUuid, mappedPkg).joinToString("|")
            val toRemove = store.keys.filter { it.startsWith(prefix) }
            toRemove.forEach { store.remove(it) }
            toRemove
        } catch (_: Exception) {
            emptyList()
        }

    /**
     * 根据 featureKey（特征 ID）后缀查找并移除匹配的 sourceId，返回被移除的 sourceId 列表。
     * 兼容只传入 featureKey 的结束包（例如仅包含 featureKeyValue），用于定位完整的 sourceId。
     */
    @Synchronized
    fun removeByFeatureKey(featureKey: String): List<String> =
        try {
            val suffix = "|$featureKey"
            val toRemove = store.keys.filter { it.endsWith(suffix) || it == featureKey }
            toRemove.forEach { store.remove(it) }
            toRemove
        } catch (_: Exception) {
            emptyList()
        }

    /**
     * 精确移除指定的 sourceId（如果存在），返回是否成功移除。
     */
    fun removeExact(sourceId: String): Boolean =
        try {
            store.remove(sourceId) != null
        } catch (_: Exception) {
            false
        }

    /**
     * 获取指定sourceId的状态，用于外部查询当前状态
     */
    fun getState(sourceId: String): DiffSystem.State? =
        try {
            store[sourceId]
        } catch (_: Exception) {
            null
        }
}
