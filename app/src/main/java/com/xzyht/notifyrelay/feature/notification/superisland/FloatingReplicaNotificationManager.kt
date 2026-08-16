package com.xzyht.notifyrelay.feature.notification.superisland

import android.app.NotificationManager
import android.content.Context
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.formatter.SuperIslandDataFormatter
import com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
import com.xzyht.notifyrelay.feature.notification.superisland.lifecycle.LiveUpdatesNotificationManager
import com.xzyht.notifyrelay.feature.notification.superisland.lifecycle.NotificationGenerator
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
            runWithErrorHandlingSuspend("发送通知") {
                val taskVersion = FloatingReplicaMappingManager.nextVersion(sourceId)

                val internedPicMap =
                    withContext(Dispatchers.IO) {
                        SuperIslandImageStore.internAll(context, sourceId, picMap)
                    }

                if (!FloatingReplicaMappingManager.isLatestVersion(sourceId, taskVersion)) {
                    return@runWithErrorHandlingSuspend
                }

                val formattedData = SuperIslandDataFormatter.formatForDisplay(context, paramV2Raw, internedPicMap)
                val paramV2 = formattedData.paramV2

                val displayTitle =
                    title?.takeIf { it.isNotBlank() }
                        ?: paramV2?.highlightInfo?.title?.takeIf { it.isNotBlank() }
                        ?: paramV2?.baseInfo?.title?.takeIf { it.isNotBlank() }

                val displayText =
                    text?.takeIf { it.isNotBlank() }
                        ?: paramV2?.highlightInfo?.content?.takeIf { it.isNotBlank() }
                        ?: paramV2?.baseInfo?.content?.takeIf { it.isNotBlank() }

                val entryKey = sourceId

                FloatingReplicaMappingManager.addSourceIdMapping(sourceId, entryKey)

                val isProgressType = SuperIslandDataFormatter.isProgressType(paramV2)

                if (isProgressType && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.BAKLAVA) {
                    runWithErrorHandlingSuspend("发送Live Updates复合通知") {
                        LiveUpdatesNotificationManager.initialize(context)
                        LiveUpdatesNotificationManager.showLiveUpdate(
                            sourceId,
                            displayTitle,
                            displayText,
                            appName,
                            formattedData,
                        )
                        val liveUpdateNotificationId = sourceId.hashCode().and(0xffff) + 10000
                        FloatingReplicaMappingManager.putNotificationId(entryKey, liveUpdateNotificationId)
                        FloatingReplicaMappingManager.addSourceIdMapping(sourceId, entryKey, liveUpdateNotificationId)
                        Logger.i(TAG, "浮窗功能关闭时发送Live Updates复合通知: sourceId=$sourceId, notificationId=$liveUpdateNotificationId")
                    }
                } else {
                    val notificationId = NotificationGenerator.sendReplicaNotification(context, entryKey, displayTitle, displayText, appName, formattedData.paramV2, formattedData.paramV2Raw, formattedData.resolvedPicMap, sourceId, FloatingReplicaWindowManager.getFloatingWindowManager())
                    FloatingReplicaMappingManager.addSourceIdMapping(sourceId, entryKey, notificationId)
                    Logger.i(TAG, "浮窗功能关闭时发送传统复刻通知: sourceId=$sourceId, notificationId=$notificationId")
                }

                val timeoutMs = 30 * 1000L

                Logger.i(TAG, "超级岛: 设置超时时间, sourceId=$sourceId, timeoutMs=$timeoutMs")

                FloatingReplicaMappingManager.cancelTimeoutJob(sourceId)
                Logger.i(TAG, "超级岛: 取消现有的超时任务（如果存在）, sourceId=$sourceId")

                val timeoutJob =
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(timeoutMs)
                        runWithErrorHandling("超时自动移除通知") {
                            Logger.i(TAG, "超级岛: 超时任务触发，准备移除通知, sourceId=$sourceId, timeoutMs=$timeoutMs")
                            FloatingReplicaWindowManager.dismissBySourceInternal(sourceId, FloatingWindowManager.RemovalReason.TIMEOUT)
                            Logger.i(TAG, "超级岛: 通知超时自动移除, sourceId=$sourceId")
                        }
                    }
                FloatingReplicaMappingManager.setTimeoutJob(sourceId, timeoutJob)
                Logger.i(TAG, "超级岛: 已启动新的超时任务, sourceId=$sourceId, timeoutMs=$timeoutMs")
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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.BAKLAVA) {
                runWithErrorHandling("关闭Live Updates通知") {
                    LiveUpdatesNotificationManager.initialize(context)
                    LiveUpdatesNotificationManager.dismissLiveUpdateNotification(sourceId)
                    Logger.i(TAG, "通过LiveUpdatesNotificationManager关闭通知: sourceId=$sourceId")

                    val liveUpdateNotificationId = sourceId.hashCode().and(0xffff) + 10000
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    try {
                        Logger.i(TAG, "尝试直接关闭Live Updates通知，sourceId=$sourceId, notificationId=$liveUpdateNotificationId")
                        notificationManager.cancel(liveUpdateNotificationId)
                        Logger.i(TAG, "直接关闭Live Updates通知成功，sourceId=$sourceId, notificationId=$liveUpdateNotificationId")
                    } catch (e: Exception) {
                        Logger.w(TAG, "直接关闭Live Updates通知失败: ${e.message}")
                    }
                }
            }

            runWithErrorHandling("关闭传统复刻通知") {
                val notificationIds = notificationIdsBefore ?: FloatingReplicaMappingManager.getNotificationIdsBySourceId(sourceId)
                Logger.i(TAG, "尝试关闭传统复刻通知，sourceId=$sourceId，notificationIds=$notificationIds")
                if (notificationIds != null && notificationIds.isNotEmpty()) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationIds.forEach { notificationId ->
                        try {
                            Logger.i(TAG, "正在取消通知，sourceId=$sourceId, notificationId=$notificationId")
                            notificationManager.cancel(notificationId)
                            Logger.i(TAG, "通过直接映射关闭通知成功，sourceId=$sourceId, notificationId=$notificationId")
                        } catch (e: Exception) {
                            Logger.w(TAG, "通过直接映射关闭通知失败: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                    FloatingReplicaMappingManager.removeSourceIdMappings(sourceId)
                    val keys = entryKeys ?: listOf(sourceId)
                    keys.forEach { entryKey ->
                        FloatingReplicaMappingManager.removeNotificationId(entryKey)
                    }
                } else {
                    Logger.w(TAG, "没有找到直接映射的 notificationIds，使用回退方案，sourceId=$sourceId")
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    try {
                        val traditionalNotificationId = sourceId.hashCode().and(0xffff) + 20000
                        Logger.i(TAG, "尝试直接关闭传统复刻通知，sourceId=$sourceId, notificationId=$traditionalNotificationId")
                        notificationManager.cancel(traditionalNotificationId)
                        Logger.i(TAG, "直接关闭传统复刻通知成功，sourceId=$sourceId, notificationId=$traditionalNotificationId")
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
                Logger.i(TAG, "关闭传统复刻通知完成: sourceId=$sourceId")
            }
        } else {
            Logger.w(TAG, "超级岛: 无法获取上下文，无法关闭通知: sourceId=$sourceId")
            FloatingReplicaMappingManager.removeSourceIdMappings(sourceId)
            val keys = entryKeys ?: listOf(sourceId)
            keys.forEach { entryKey ->
                FloatingReplicaMappingManager.removeNotificationId(entryKey)
            }
        }

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
            Logger.i(TAG, "超级岛: 浮窗功能已关闭，直接关闭通知，notificationId=$notificationId")

            if (sourceIdToStop != null) {
                FloatingReplicaWindowManager.dismissBySourceInternal(sourceIdToStop, FloatingWindowManager.RemovalReason.MANUAL)
            } else {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                try {
                    notificationManager.cancel(notificationId)
                    Logger.i(TAG, "超级岛: 直接关闭通知成功，notificationId=$notificationId")
                } catch (e: Exception) {
                    Logger.w(TAG, "超级岛: 直接关闭通知失败: ${e.message}")
                }
            }
            return
        }

        if (sourceIdToStop != null) {
            NotificationGenerator.stopScrollUpdate(sourceIdToStop)
            Logger.i(TAG, "超级岛: 关闭通知时停止滚动更新, sourceId=$sourceIdToStop, notificationId=$notificationId")
        }

        val entryKey = FloatingReplicaMappingManager.getEntryKeyByNotificationId(notificationId)

        if (entryKey != null) {
            FloatingReplicaWindowManager.getFloatingWindowManager().removeEntry(entryKey, FloatingWindowManager.RemovalReason.MANUAL)
            Logger.i(TAG, "超级岛: 根据通知ID关闭浮窗条目成功，notificationId=$notificationId, entryKey=$entryKey")
        } else {
            Logger.w(TAG, "超级岛: 未找到通知ID对应的浮窗条目，notificationId=$notificationId")
        }
    }

    private inline fun runWithErrorHandling(
        actionName: String,
        crossinline block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: $actionName 失败: ${e.message}")
        }
    }

    private suspend inline fun runWithErrorHandlingSuspend(
        actionName: String,
        crossinline block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: $actionName 失败: ${e.message}")
        }
    }
}
