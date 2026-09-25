package com.xzyht.notifyrelay.feature.notification.superisland.replica

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.notification.LiveUpdatesNotificationManager
import com.xzyht.notifyrelay.feature.notification.superisland.notification.NotificationGenerator
import com.xzyht.notifyrelay.feature.notification.superisland.notification.SuperIslandNotificationIds
import com.xzyht.notifyrelay.feature.notification.superisland.pipeline.SuperIslandDisplayPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import notifyrelay.base.util.Logger

object FloatingReplicaNotificationManager {
    private const val TAG = "超级岛通知管理"

    fun sendNotification(
        context: Context,
        sourceId: String,
        title: String?,
        text: String?,
        paramV2Raw: String?,
        picMap: Map<String, String>?,
        appName: String?,
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            runReplicaCatchingSuspend(TAG, "发送通知") {
                // 展示管线（三通道共享）：本通道无额外前置守卫
                val dispatched =
                    SuperIslandDisplayPipeline.dispatch(
                        context = context,
                        request =
                            SuperIslandDisplayPipeline.DisplayRequest(
                                sourceId = sourceId,
                                title = title,
                                text = text,
                                paramV2Raw = paramV2Raw,
                                picMap = picMap,
                                appName = appName,
                                channel = SuperIslandDisplayPipeline.Channel.NOTIFICATION,
                                tag = TAG,
                                // 竞态守卫：协程 nextVersion 可能在 dismissBySource 的 removeSourceIdMappings 之后执行，
                                // 导致版本被 computeIfAbsent 重建、isLatestVersion 误判通过。
                                // 此处复检 isSourceRecentlyClosed（dismissBySource 已 markSourceClosed），命中即中止。
                                extraGuard = { ReplicaTtlRegistry.isSourceRecentlyClosed(sourceId) },
                                abortMessage = "在异步发送期间被关闭，中止发送",
                                skipRefreshMessage = "内容无变更，跳过系统通知刷新",
                                registerMappingBeforeSend = true,
                                registerMappingOnSendFailure = true,
                                registerLiveUpdateMappingRegardlessOfSuccess = true,
                            ),
                    )

                if (!dispatched) return@runReplicaCatchingSuspend

                ReplicaStateStore.cancelTimeoutJob(sourceId)
                val timeoutJob =
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(30_000L)
                        runReplicaCatching(TAG, "超时自动移除通知") {
                            FloatingReplicaManager.dismissBySourceInternal(sourceId, FloatingWindowManager.RemovalReason.TIMEOUT)
                        }
                    }
                ReplicaStateStore.setTimeoutJob(sourceId, timeoutJob)
            }
        }
    }

    fun closeNotificationsBySourceId(
        sourceId: String,
        reason: FloatingWindowManager.RemovalReason,
        notificationIdsBefore: List<Int>?,
        entryKeys: List<String>?,
        context: Context?,
    ) {
        if (context != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                runReplicaCatching(TAG, "关闭Live Updates通知") {
                    // 单入口：dismiss 内部取消的正是 SuperIslandNotificationIds.liveUpdates(sourceId)，
                    // 原实现在此处再直接 cancel 同一 id 属重复操作（D7）
                    LiveUpdatesNotificationManager.dismiss(sourceId, context)
                }
            }

            runReplicaCatching(TAG, "关闭传统复刻通知") {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                try {
                    notificationManager.cancel(SuperIslandNotificationIds.replica(sourceId))
                } catch (e: Exception) {
                    Logger.w(TAG, "直接关闭传统复刻通知失败: ${e.message}")
                }

                val keys = entryKeys ?: listOf(sourceId)
                keys.forEach { entryKey ->
                    NotificationGenerator.cancelReplicaNotification(context, entryKey)
                }
                if (keys.isEmpty()) {
                    NotificationGenerator.clearAllReplicaNotifications(context)
                }
            }
        } else {
            Logger.w(TAG, "超级岛: 无法获取上下文，无法关闭通知: sourceId=$sourceId")
            ReplicaStateStore.removeSourceIdMappings(sourceId)
            val keys = entryKeys ?: listOf(sourceId)
            keys.forEach { entryKey ->
                ReplicaStateStore.removeNotificationId(entryKey)
            }
        }

        // 通知已撤回，清理内容指纹，保证后续保活包（即使无变更）会重新发出通知
        ReplicaStateStore.removeNotificationFingerprint(sourceId)

        if (reason == FloatingWindowManager.RemovalReason.REMOTE || reason == FloatingWindowManager.RemovalReason.TIMEOUT) {
            ReplicaTtlRegistry.removeBlockedInstance(sourceId)
        }
    }

    fun closeNotificationByNotificationId(
        context: Context,
        notificationId: Int,
    ) {
        val sourceIdToStop = ReplicaStateStore.findSourceIdByNotificationId(notificationId)

        val isFloatingEnabled = SuperIslandConfigUtils.isFloatingWindowEnabled(context)

        if (!isFloatingEnabled) {
            if (sourceIdToStop != null) {
                FloatingReplicaManager.dismissBySourceInternal(sourceIdToStop, FloatingWindowManager.RemovalReason.MANUAL)
            } else {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                try {
                    notificationManager.cancel(notificationId)
                } catch (e: Exception) {
                    Logger.w(TAG, "超级岛: 直接关闭通知失败: ${e.message}")
                }
            }
            return
        }

        if (sourceIdToStop != null) {
            NotificationGenerator.stopScrollUpdate(sourceIdToStop)
        }

        val entryKey = ReplicaStateStore.getEntryKeyByNotificationId(notificationId)

        if (entryKey != null) {
            FloatingReplicaWindowManager.getFloatingWindowManager().removeEntry(entryKey, FloatingWindowManager.RemovalReason.MANUAL)
        } else {
            Logger.w(TAG, "超级岛: 未找到通知ID对应的浮窗条目，notificationId=$notificationId")
        }
    }
}
