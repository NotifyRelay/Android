package com.xzyht.notifyrelay.feature.notification.superisland.replica

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.formatter.SuperIslandDataFormatter
import com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
import com.xzyht.notifyrelay.feature.notification.superisland.notification.LiveUpdatesNotificationManager
import com.xzyht.notifyrelay.feature.notification.superisland.notification.NotificationGenerator
import com.xzyht.notifyrelay.feature.notification.superisland.notification.SuperIslandNotificationIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        isLocked: Boolean = false,
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            runReplicaCatchingSuspend(TAG, "发送通知") {
                val taskVersion = FloatingReplicaMappingManager.nextVersion(sourceId)

                val internedPicMap =
                    withContext(Dispatchers.IO) {
                        SuperIslandImageStore.internAll(context, sourceId, picMap)
                    }

                if (!FloatingReplicaMappingManager.isLatestVersion(sourceId, taskVersion)) {
                    return@runReplicaCatchingSuspend
                }

                // 竞态守卫：协程 nextVersion 可能在 dismissBySource 的 removeSourceIdMappings 之后执行，
                // 导致版本被 computeIfAbsent 重建、isLatestVersion 误判通过。
                // 此处复检 isSourceRecentlyClosed（dismissBySource 已 markSourceClosed），命中即中止。
                if (FloatingReplicaMappingManager.isSourceRecentlyClosed(sourceId)) {
                    Logger.i(TAG, "超级岛: sourceId=$sourceId 在异步发送期间被关闭，中止发送")
                    return@runReplicaCatchingSuspend
                }

                val formattedData = SuperIslandDataFormatter.formatForDisplay(context, paramV2Raw, internedPicMap)
                val paramV2 = formattedData.paramV2

                val displayTitle =
                    title?.takeIf { it.isNotBlank() }
                        ?: paramV2?.highlightInfo?.title?.takeIf { it.isNotBlank() }
                        ?: paramV2?.baseInfo?.title?.takeIf { it.isNotBlank() }
                        ?: "未知"

                val displayText =
                    text?.takeIf { it.isNotBlank() }
                        ?: paramV2?.highlightInfo?.content?.takeIf { it.isNotBlank() }
                        ?: paramV2?.baseInfo?.content?.takeIf { it.isNotBlank() }
                        ?: "未知"

                val entryKey = sourceId

                FloatingReplicaMappingManager.addSourceIdMapping(sourceId, entryKey)

                val isProgressType = SuperIslandDataFormatter.isProgressType(paramV2)

                // 注入模式：超级岛模式优先于 Live Updates 模式（对齐媒体类型的既有分流范式）。
                // 超级岛模式下，即便含 progressInfo 也走超级岛通道，确保拿到 addSuperIslandStructuredData 注入；
                // 仅在「Live Updates 注入且非超级岛」时保留现有 Live Updates 通道。
                val superIslandMode = SuperIslandConfigUtils.isSuperIslandSpecInjectionEnabled(context)
                val liveUpdatesMode = SuperIslandConfigUtils.isLiveUpdatesSpecInjectionEnabled(context)
                val injectionModeOrdinal = SuperIslandConfigUtils.getSpecInjectionMode(context).ordinal

                // 注入模式变化时先取消旧通知并清理旧映射，避免两个通道的通知并存/残留
                FloatingReplicaMappingManager.migrateInjectionModeIfChanged(context, sourceId, injectionModeOrdinal)

                // 内容与上次成功发出的通知一致且通知仍在展示时，跳过系统通知刷新（不调用 notify），
                // 仅保留下方超时计时器的重置。
                // 超级岛信息注入模式下重发会触发系统展开态悬浮（islandFirstFloat 默认 true），影响用户体感；
                // Live Updates 模式走系统兼容转换，重发虽无展开态副作用，但内容不变时同样无需重发。
                // 指纹包含注入模式：模式变化时指纹随之变化，不会被误判为「内容无变更」。
                val fingerprint =
                    FloatingReplicaMappingManager.computeNotificationFingerprint(
                        displayTitle,
                        displayText,
                        formattedData.paramV2Raw,
                        formattedData.resolvedPicMap,
                        injectionModeOrdinal,
                    )
                val previousNotificationIds = FloatingReplicaMappingManager.getNotificationIdsBySourceId(sourceId)
                val canSkipRefresh =
                    !previousNotificationIds.isNullOrEmpty() &&
                        FloatingReplicaMappingManager.isAnyNotificationActive(context, previousNotificationIds) &&
                        fingerprint == FloatingReplicaMappingManager.getNotificationFingerprint(sourceId)

                if (canSkipRefresh) {
                    Logger.i(TAG, "超级岛: 内容无变更，跳过系统通知刷新: sourceId=$sourceId")
                } else if (liveUpdatesMode && !superIslandMode && isProgressType && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    runReplicaCatchingSuspend(TAG, "发送Live Updates复合通知") {
                        LiveUpdatesNotificationManager.initialize(context)
                        val success =
                            LiveUpdatesNotificationManager.showLiveUpdate(
                                sourceId,
                                displayTitle,
                                displayText,
                                appName,
                                formattedData,
                            )
                        val liveUpdateNotificationId = SuperIslandNotificationIds.liveUpdates(sourceId)
                        FloatingReplicaMappingManager.putNotificationId(entryKey, liveUpdateNotificationId)
                        FloatingReplicaMappingManager.addSourceIdMapping(sourceId, entryKey, liveUpdateNotificationId)
                        if (success) {
                            FloatingReplicaMappingManager.setNotificationFingerprint(sourceId, fingerprint)
                        }
                    }
                } else {
                    val notificationId = NotificationGenerator.sendReplicaNotification(context, entryKey, displayTitle, displayText, appName, formattedData.paramV2, formattedData.paramV2Raw, formattedData.resolvedPicMap, sourceId, FloatingReplicaWindowManager.getFloatingWindowManager())
                    FloatingReplicaMappingManager.addSourceIdMapping(sourceId, entryKey, notificationId)
                    if (notificationId != null) {
                        FloatingReplicaMappingManager.setNotificationFingerprint(sourceId, fingerprint)
                    }
                }

                FloatingReplicaMappingManager.cancelTimeoutJob(sourceId)
                val timeoutJob =
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(30_000L)
                        runReplicaCatching(TAG, "超时自动移除通知") {
                            FloatingReplicaWindowManager.dismissBySourceInternal(sourceId, FloatingWindowManager.RemovalReason.TIMEOUT)
                        }
                    }
                FloatingReplicaMappingManager.setTimeoutJob(sourceId, timeoutJob)
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
                    LiveUpdatesNotificationManager.initialize(context)
                    LiveUpdatesNotificationManager.dismissLiveUpdateNotification(sourceId)

                    val liveUpdateNotificationId = SuperIslandNotificationIds.liveUpdates(sourceId)
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    try {
                        notificationManager.cancel(liveUpdateNotificationId)
                    } catch (e: Exception) {
                        Logger.w(TAG, "直接关闭Live Updates通知失败: ${e.message}")
                    }
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
            FloatingReplicaMappingManager.removeSourceIdMappings(sourceId)
            val keys = entryKeys ?: listOf(sourceId)
            keys.forEach { entryKey ->
                FloatingReplicaMappingManager.removeNotificationId(entryKey)
            }
        }

        // 通知已撤回，清理内容指纹，保证后续保活包（即使无变更）会重新发出通知
        FloatingReplicaMappingManager.removeNotificationFingerprint(sourceId)

        if (reason == FloatingWindowManager.RemovalReason.REMOTE || reason == FloatingWindowManager.RemovalReason.TIMEOUT) {
            FloatingReplicaMappingManager.removeBlockedInstance(sourceId)
        }
    }

    fun closeNotificationByNotificationId(
        context: Context,
        notificationId: Int,
    ) {
        val sourceIdToStop = FloatingReplicaMappingManager.findSourceIdByNotificationId(notificationId)

        val isFloatingEnabled = FloatingReplicaWindowManager.isFloatingWindowEnabled(context)

        if (!isFloatingEnabled) {
            if (sourceIdToStop != null) {
                FloatingReplicaWindowManager.dismissBySourceInternal(sourceIdToStop, FloatingWindowManager.RemovalReason.MANUAL)
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

        val entryKey = FloatingReplicaMappingManager.getEntryKeyByNotificationId(notificationId)

        if (entryKey != null) {
            FloatingReplicaWindowManager.getFloatingWindowManager().removeEntry(entryKey, FloatingWindowManager.RemovalReason.MANUAL)
        } else {
            Logger.w(TAG, "超级岛: 未找到通知ID对应的浮窗条目，notificationId=$notificationId")
        }
    }
}
