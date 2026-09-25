package com.xzyht.notifyrelay.feature.notification.superisland.replica

import notifyrelay.base.util.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * 超级岛复刻通道的两组**时效屏蔽**登记表。
 *
 * 由原 `FloatingReplicaMappingManager` 拆分而来，语义与原实现逐字一致：
 * - `closedSourceIds` / `closedSourceVersions`：sourceId 被关闭后的 30s / 60s 屏蔽窗口；
 * - `blockedInstanceIds`：会话级屏蔽，15s 内被重复访问则续期。
 *
 * 这两组状态在 [ReplicaStateStore.clearAllMappings] 中**刻意不被清理**：
 * 通道切换不应解除用户刚刚表达过的关闭意图，两者仍按各自 TTL 自然过期。
 */
internal object ReplicaTtlRegistry {
    private const val TAG = "超级岛映射管理"

    private val closedSourceIds = ConcurrentHashMap<String, Long>()
    private val closedSourceVersions = ConcurrentHashMap<String, Long>()

    private val blockedInstanceIds = ConcurrentHashMap<String, Long>()
    private const val BLOCK_EXPIRE_MS = 15_000L

    fun isInstanceBlocked(instanceId: String?): Boolean {
        if (instanceId.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        val ts = blockedInstanceIds[instanceId] ?: return false
        if (now - ts > BLOCK_EXPIRE_MS) {
            blockedInstanceIds.remove(instanceId)
            Logger.i(TAG, "超级岛: 屏蔽过期，自动移除 instanceId=$instanceId")
            return false
        }
        blockedInstanceIds[instanceId] = now
        return true
    }

    fun blockInstance(instanceId: String?) {
        if (instanceId.isNullOrBlank()) return
        blockedInstanceIds[instanceId] = System.currentTimeMillis()
        Logger.i(TAG, "超级岛: 会话级屏蔽 instanceId=$instanceId")
    }

    fun removeBlockedInstance(instanceId: String) {
        blockedInstanceIds.remove(instanceId)
    }

    fun isSourceRecentlyClosed(sourceId: String): Boolean {
        val lastClosed = closedSourceIds[sourceId]
        return lastClosed != null && (System.currentTimeMillis() - lastClosed) < 30_000L
    }

    fun markSourceClosed(sourceId: String) {
        closedSourceIds[sourceId] = System.currentTimeMillis()
        ReplicaStateStore.currentVersion(sourceId)?.let { closedSourceVersions[sourceId] = it }
    }

    fun removeClosedSource(sourceId: String) {
        closedSourceIds.remove(sourceId)
    }

    fun isSourceRecentlyClosedWithinMinute(sourceId: String): Boolean {
        val lastClosed = closedSourceIds[sourceId] ?: return false
        if (System.currentTimeMillis() - lastClosed >= 60_000L) return false
        val closedVersion = closedSourceVersions[sourceId] ?: return false
        val currentVersion = ReplicaStateStore.currentVersion(sourceId) ?: return false
        return currentVersion == closedVersion
    }
}
