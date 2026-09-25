package com.xzyht.notifyrelay.feature.notification.superisland.replica

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.xzyht.notifyrelay.feature.notification.service.ListenerForegroundController
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.list.FloatingReplicaListModeManager
import com.xzyht.notifyrelay.feature.notification.superisland.list.SuperIslandListManager
import com.xzyht.notifyrelay.feature.notification.superisland.notification.LiveUpdatesNotificationManager
import com.xzyht.notifyrelay.feature.notification.superisland.notification.NotificationGenerator
import notifyrelay.base.util.Logger

object FloatingReplicaManager {
    private const val TAG = "超级岛复刻实现骨架"

    private var appContext: Context? = null

    fun getAppContext(): Context? = appContext

    fun isSourceRecentlyClosed(sourceId: String): Boolean = ReplicaTtlRegistry.isSourceRecentlyClosed(sourceId)

    fun showFloating(
        context: Context,
        sourceId: String,
        title: String?,
        text: String?,
        paramV2Raw: String? = null,
        picMap: Map<String, String>? = null,
        appName: String? = null,
        isLocked: Boolean = false,
        cacheForChannelSwitch: Boolean = true,
    ) {
        appContext = context.applicationContext
        ReplicaStateStore.setAppContext(appContext)

        // 登记「当前展示内容」，供通道切换时按新通道重建。
        // 远端媒体胶囊传 false：媒体有独立开关，不参与超级岛通道迁移。
        fun cacheDisplay() {
            if (cacheForChannelSwitch) {
                ReplicaDisplayCache.put(sourceId, title, text, paramV2Raw, picMap, appName, isLocked)
            }
        }

        val isRecentlyClosed = ReplicaTtlRegistry.isSourceRecentlyClosed(sourceId)

        if (SuperIslandConfigUtils.isFloatingWindowEnabled(context)) {
            if (isRecentlyClosed) {
                Logger.i(TAG, "超级岛: sourceId=$sourceId 在30秒内被关闭过，跳过浮窗展示")
                return
            }
            cacheDisplay()
            FloatingReplicaWindowManager.showFloatingInternal(context, sourceId, title, text, paramV2Raw, picMap, appName, isLocked, false)
        } else if (isRecentlyClosed && SuperIslandListManager.containsSourceId(sourceId)) {
            Logger.i(TAG, "超级岛: sourceId=$sourceId 在30秒内被关闭过且条目仍在列表中，跳过展示")
            return
        } else if (SuperIslandConfigUtils.isNotificationListMode(context)) {
            cacheDisplay()
            ReplicaTtlRegistry.removeClosedSource(sourceId)
            FloatingReplicaListModeManager.showFloatingListMode(context, sourceId, title, text, paramV2Raw, picMap, appName, isLocked)
        } else {
            cacheDisplay()
            ReplicaTtlRegistry.removeClosedSource(sourceId)
            FloatingReplicaNotificationManager.sendNotification(context, sourceId, title, text, paramV2Raw, picMap, appName, isLocked)
        }
    }

    fun toggleFloating(
        context: Context,
        sourceId: String,
        title: String?,
        text: String?,
        paramV2Raw: String? = null,
        picMap: Map<String, String>? = null,
        appName: String? = null,
    ) {
        FloatingReplicaWindowManager.toggleFloating(context, sourceId, title, text, paramV2Raw, picMap, appName)
    }

    fun closeByNotificationId(notificationId: Int) {
        runReplicaCatching(TAG, "根据通知ID关闭浮窗条目") {
            val ctx = appContext ?: return@runReplicaCatching

            if (!SuperIslandConfigUtils.isFloatingWindowEnabled(ctx) && SuperIslandConfigUtils.isNotificationListMode(ctx)) {
                FloatingReplicaListModeManager.closeListModeNotification(ctx, notificationId)
                return@runReplicaCatching
            }

            FloatingReplicaNotificationManager.closeNotificationByNotificationId(ctx, notificationId)
        }
    }

    fun dismissBySource(sourceId: String) {
        dismissBySourceInternal(sourceId, FloatingWindowManager.RemovalReason.REMOTE)
    }

    /**
     * **关闭侧的统一分发门面**（P2-7：自 `FloatingReplicaWindowManager.dismissBySourceInternal` 上移）。
     *
     * 与展示侧的 [showFloating] 对称：两者都在本门面按通道分流。原先关闭侧藏在窗口管理器里
     * 自判通道（列表 / 浮窗 / 通知），与展示侧的分支重复且使窗口管理器承担了不属于它的职责。
     *
     * 关闭全部展示的顺序契约见 [dismissAllRemoteSuperIsland]；本方法只处理单条 sourceId。
     *
     * @param reason 移除原因；`HIDDEN` 保留展示内容缓存（隐藏是可恢复的临时状态）。
     */
    fun dismissBySourceInternal(
        sourceId: String,
        reason: FloatingWindowManager.RemovalReason = FloatingWindowManager.RemovalReason.REMOTE,
    ) {
        runReplicaCatching(TAG, "按来源关闭浮窗") {
            if (ReplicaTtlRegistry.isSourceRecentlyClosedWithinMinute(sourceId)) {
                return@runReplicaCatching
            }

            if (reason != FloatingWindowManager.RemovalReason.HIDDEN) {
                ReplicaTtlRegistry.markSourceClosed(sourceId)
                // HIDDEN 保留缓存：隐藏是可恢复的临时状态，内容仍然活跃、切换通道时仍应展示
                ReplicaDisplayCache.remove(sourceId)
            }

            ReplicaStateStore.cancelTimeoutJob(sourceId)

            NotificationGenerator.stopScrollUpdate(sourceId)

            val ctx = ReplicaStateStore.getAppContext()
            // 列表模式通道：交由列表管理器摘除条目并切换下一条
            if (ctx != null && !SuperIslandConfigUtils.isFloatingWindowEnabled(ctx) && SuperIslandConfigUtils.isNotificationListMode(ctx)) {
                FloatingReplicaListModeManager.dismissFromList(ctx, sourceId)
                if (reason == FloatingWindowManager.RemovalReason.REMOTE || reason == FloatingWindowManager.RemovalReason.TIMEOUT) {
                    ReplicaTtlRegistry.removeBlockedInstance(sourceId)
                }
                return@runReplicaCatching
            }

            val floatingEnabled = if (ctx != null) SuperIslandConfigUtils.isFloatingWindowEnabled(ctx) else true

            val notificationIdsBefore = ReplicaStateStore.getNotificationIdsBySourceId(sourceId)
            val entryKeys = ReplicaStateStore.getSourceIdEntryKeys(sourceId)

            // 浮窗通道：移除 overlay 条目（含映射清理）
            if (floatingEnabled) {
                FloatingReplicaWindowManager.removeFloatingEntries(
                    sourceId = sourceId,
                    reason = reason,
                    entryKeys = entryKeys,
                    removeMappings = true,
                )
            }

            // 系统通知通道（复刻 / Live Updates）由通知管理器关闭
            FloatingReplicaNotificationManager.closeNotificationsBySourceId(sourceId, reason, notificationIdsBefore, entryKeys, ctx)
        }
    }

    /**
     * 撤下全部远端超级岛展示，并清空内部通道状态。
     *
     * 使用场景：
     * - 「超级岛显示」开关关闭：立即关闭浮窗、列表与系统通知，不再展示远端超级岛；
     * - 通道切换的撤下阶段（见 [switchSuperIslandChannel]）。
     *
     * 只清理展示与通道状态：远端状态缓存（SuperIslandRemoteStore）与历史记录不受影响。
     * 「当前展示内容」缓存（[ReplicaDisplayCache]）也被清空——通道切换会在撤下前先取快照，
     * 重建时再经 [showFloating] 重新登记。
     */
    fun dismissAllRemoteSuperIsland(context: Context) {
        runReplicaCatching(TAG, "关闭全部远端超级岛展示") {
            val ctx = context.applicationContext
            val sourceIds = ReplicaStateStore.getAllSourceIds()

            // Live Updates 通道（仅 Android 16+）：按 sourceId 关闭系统提升通知
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                runReplicaCatching(TAG, "关闭全部Live Updates通知") {
                    LiveUpdatesNotificationManager.initialize(ctx)
                    sourceIds.forEach { sourceId ->
                        LiveUpdatesNotificationManager.dismiss(sourceId)
                    }
                }
            }

            // 列表模式：清空聚合列表条目并撤下固定 ID 的聚合通知
            SuperIslandListManager.clear()
            runReplicaCatching(TAG, "关闭列表模式聚合通知") {
                val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(FloatingReplicaListModeManager.getNotificationId())
            }

            // 普通复刻通道：先按映射撤下系统通知，再清空浮窗条目。
            // 顺序不可颠倒：清空浮窗条目会触发 onEntriesEmpty（移除浮窗容器并再清一次映射），
            // 映射被清空后 getAllNotificationIds 为空，此处的系统通知就再也取消不掉了。
            NotificationGenerator.clearAllReplicaNotifications(ctx)
            FloatingReplicaWindowManager.getFloatingWindowManager().clearAllEntries()

            // 清空全部通道映射与内容指纹，保证后续远端包按当前通道重新建立映射
            ReplicaStateStore.clearAllMappings()

            ReplicaDisplayCache.clear()

            // 列表已被清空（含「超级岛显示」关闭），同步收起前台常驻通知的「可切换」提示
            ListenerForegroundController.onSuperIslandListChanged()
        }
    }

    /**
     * 切换展示通道：撤下旧通道展示，并把「当前展示内容」按新通道重新展示。
     *
     * **为什么必须重建**：旧通道与新通道的通知 ID 与渲染方式均不同，撤下旧通道后若不重建，
     * 内容会一直空到远端下一个包到来；一次性通知（验证码等）甚至永远不会再出现。
     *
     * 调用方需保证两个通道开关**已写入新值**，本方法按当时的配置经 [showFloating] 分流重建。
     * 「超级岛显示」关闭时不重建，只做撤下。
     */
    fun switchSuperIslandChannel(context: Context) {
        if (!SuperIslandConfigUtils.isRemoteSuperIslandDisplayEnabled(context)) {
            dismissAllRemoteSuperIsland(context)
            return
        }

        val snapshot = ReplicaDisplayCache.snapshot()
        dismissAllRemoteSuperIsland(context)

        snapshot.forEach { entry ->
            try {
                showFloating(
                    context = context,
                    sourceId = entry.sourceId,
                    title = entry.title,
                    text = entry.text,
                    paramV2Raw = entry.paramV2Raw,
                    picMap = entry.picMap,
                    appName = entry.appName,
                    isLocked = entry.isLocked,
                )
            } catch (e: Exception) {
                Logger.w(TAG, "超级岛: 切换通道重建展示失败: sourceId=${entry.sourceId}, ${e.message}")
            }
        }
        Logger.i(TAG, "超级岛: 展示通道已切换，重建 ${snapshot.size} 条当前展示内容")
    }
}
