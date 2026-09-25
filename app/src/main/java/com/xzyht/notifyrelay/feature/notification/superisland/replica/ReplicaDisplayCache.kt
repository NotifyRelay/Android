package com.xzyht.notifyrelay.feature.notification.superisland.replica

import java.util.concurrent.ConcurrentHashMap

/**
 * 远端超级岛「当前展示内容」缓存。
 *
 * **为什么需要本缓存**：远端超级岛有三种展示通道（浮窗 / 列表聚合 / 普通通知），
 * 三者的通知 ID 与渲染方式互不相同。通道切换时必须先撤下旧通道通知，
 * 否则旧通知残留或与新通道通知并存；而**撤下后若不重建**，内容会一直空到远端下一个包到来，
 * 一次性通知（验证码等）甚至永远不会再出现。
 *
 * 本缓存保存「当前正在展示」的远端超级岛内容，作为通道切换时的重建来源：
 * - **写入**：每次经 [FloatingReplicaManager.showFloating] 真正登记展示时。
 *   远端媒体胶囊不走本缓存（媒体有独立开关，不参与超级岛通道迁移）。
 * - **移除**：结束包（`SuperIslandProcessor`）、超时 / 用户关闭（`HIDDEN` 除外——
 *   隐藏是可恢复的临时状态，内容仍然活跃）。
 * - **清空**：「超级岛显示」关闭 / 通道切换撤下全部展示时；
 *   通道切换会先取快照，重建时经 [FloatingReplicaManager.showFloating] 重新写入。
 *
 * 仅保存内容本身，不持有通知与视图引用；重建一律走 [FloatingReplicaManager.showFloating]，
 * 由它按当时的通道配置分流。
 */
internal object ReplicaDisplayCache {
    /** 缓存条目：与 [FloatingReplicaManager.showFloating] 的展示入参一一对应。 */
    data class Entry(
        val sourceId: String,
        val title: String?,
        val text: String?,
        val paramV2Raw: String?,
        val picMap: Map<String, String>?,
        val appName: String?,
        val isLocked: Boolean,
        val updatedAt: Long,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    fun put(
        sourceId: String,
        title: String?,
        text: String?,
        paramV2Raw: String?,
        picMap: Map<String, String>?,
        appName: String?,
        isLocked: Boolean,
    ) {
        if (sourceId.isBlank()) return
        entries[sourceId] =
            Entry(
                sourceId = sourceId,
                title = title,
                text = text,
                paramV2Raw = paramV2Raw,
                picMap = picMap,
                appName = appName,
                isLocked = isLocked,
                updatedAt = System.currentTimeMillis(),
            )
    }

    fun remove(sourceId: String) {
        if (sourceId.isBlank()) return
        entries.remove(sourceId)
    }

    /** 按登记时间升序快照，与展示顺序（先出现的内容在前）一致。 */
    fun snapshot(): List<Entry> = entries.values.sortedBy { it.updatedAt }

    fun clear() {
        entries.clear()
    }
}
