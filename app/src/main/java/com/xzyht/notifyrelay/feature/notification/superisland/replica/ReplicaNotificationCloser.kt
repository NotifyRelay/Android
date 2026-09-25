package com.xzyht.notifyrelay.feature.notification.superisland.replica

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.notification.LiveUpdatesNotificationManager
import com.xzyht.notifyrelay.feature.notification.superisland.notification.SuperIslandNotificationIds
import notifyrelay.base.util.Logger

/**
 * 超级岛复刻通道中**带副作用的关闭动作**。
 *
 * 由原 `FloatingReplicaMappingManager` 拆分而来：原实现在「状态对象」里直接持有 Context 并
 * cancel 系统通知（`migrateInjectionModeIfChanged` / `handleRemovalReason`），
 * 状态与副作用混杂。拆分后状态只留在 [ReplicaStateStore]，取消通知的副作用集中到本文件。
 *
 * 日志 TAG 沿用原值 `超级岛映射管理`，保证拆分前后日志逐字不变。
 */
internal object ReplicaNotificationCloser {
    private const val TAG = "超级岛映射管理"

    /**
     * 按通知 id 逐个撤下（失败只记日志，不抛出）。
     *
     * 原实现中有三处逐字相同的「遍历 id → cancel → catch 记日志」，
     * 现统一到此处（注入模式迁移 / 传统复刻通知 / Live Updates 直撤路径共用）。
     *
     * @return 实际尝试取消的 id 数量（失败也计入，与原实现的 `cancelled++` 口径一致）
     */
    fun cancelNotificationIds(
        context: Context,
        ids: List<Int>,
        failureMessage: String,
    ): Int {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var cancelled = 0
        ids.forEach { id ->
            try {
                notificationManager.cancel(id)
                cancelled++
            } catch (e: Exception) {
                Logger.w(TAG, "$failureMessage: id=$id, ${e.message}")
            }
        }
        return cancelled
    }

    /**
     * 注入模式变化时的共享迁移逻辑：先取消该 sourceId 的旧通知并移除旧映射，
     * 再允许调用方按新注入模式发送通知。
     *
     * 为什么必须迁移：超级岛通道与 Live Updates 通道使用**不同的 notificationId**
     * （由 [SuperIslandNotificationIds] 按通道基址 + 16 位哈希推导，两通道基址间距大于哈希空间，
     * 区间互不重叠；列表模式为固定 30000），
     * 且渲染方式不同；若仅在旧通知上叠加，会出现旧通知残留、两条通知并存或旧模式内容不更新。
     *
     * 同时清理内容指纹：指纹只描述内容，不含模式，模式变化后若沿用旧指纹，
     * 后续保活包会被 canSkipRefresh 误判为「无变更」而永不重发。
     *
     * @return 实际取消的旧通知数量
     */
    fun migrateInjectionModeIfChanged(
        context: Context,
        sourceId: String,
        currentModeOrdinal: Int,
    ): Int {
        val previous = ReplicaStateStore.getInjectionMode(sourceId)
        if (previous == null || previous == currentModeOrdinal) {
            // 首次发送或模式未变：仅确保记录存在
            ReplicaStateStore.setInjectionMode(sourceId, currentModeOrdinal)
            return 0
        }

        // 取消旧通知：先按映射取实际通知 id，再兜底取消两个通道的推导 id，
        // 避免映射缺失时旧通知残留在通知栏
        val mappedIds = ReplicaStateStore.removeNotificationIdsBySourceId(sourceId)
        // 兜底取消两个通道的推导 id，避免映射缺失时旧通知残留在通知栏。
        // 统一走 SuperIslandNotificationIds，保证与发送侧使用完全相同的推导公式。
        val fallbackIds =
            listOf(
                SuperIslandNotificationIds.liveUpdates(sourceId), // Live Updates 通道
                SuperIslandNotificationIds.replica(sourceId), // 复刻通道路径
            )
        val cancelled =
            cancelNotificationIds(
                context = context,
                ids = (mappedIds.orEmpty() + fallbackIds).distinct(),
                failureMessage = "切换注入模式时取消旧通知失败: sourceId=$sourceId",
            )

        // 移除旧映射与旧指纹，保证后续按新模式重新建立映射、且不会被指纹跳过
        ReplicaStateStore.removeSourceIdMappings(sourceId)
        ReplicaStateStore.removeNotificationFingerprint(sourceId)
        ReplicaStateStore.setInjectionMode(sourceId, currentModeOrdinal)

        Logger.i(TAG, "注入模式变化($previous→$currentModeOrdinal)，已取消旧通知 $cancelled 条并清理旧映射: sourceId=$sourceId")
        return cancelled
    }

    /**
     * 浮窗条目被移除时的收尾：按原因做会话级屏蔽，并撤下对应的 Live Updates 复合通知。
     *
     * 取消 Live Updates 通知需要 Context，原实现从浮窗容器 View 的弱引用取（见
     * [ReplicaStateStore.getOverlayContext]），此处保持同一来源。
     */
    fun handleRemovalReason(
        sourceId: String,
        reason: FloatingWindowManager.RemovalReason,
    ) {
        if (reason == FloatingWindowManager.RemovalReason.MANUAL || reason == FloatingWindowManager.RemovalReason.HIDDEN) {
            ReplicaTtlRegistry.blockInstance(sourceId)
        }

        if (reason != FloatingWindowManager.RemovalReason.HIDDEN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            runReplicaCatching(TAG, "关闭Live Updates复合通知") {
                val context = ReplicaStateStore.getOverlayContext()
                if (context != null) {
                    LiveUpdatesNotificationManager.dismiss(sourceId, context)
                } else {
                    Logger.w(TAG, "无法关闭Live Updates复合通知，上下文为空")
                }
            }
        }
    }
}
