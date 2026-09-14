package com.xzyht.notifyrelay.feature.notification.superisland.replica

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.formatter.SuperIslandDataFormatter
import com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
import com.xzyht.notifyrelay.feature.notification.superisland.notification.LiveUpdatesNotificationManager
import com.xzyht.notifyrelay.feature.notification.superisland.notification.NotificationGenerator
import com.xzyht.notifyrelay.feature.notification.superisland.notification.SuperIslandListManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notifyrelay.base.util.Logger
import org.json.JSONObject

object FloatingReplicaListModeManager {
    private const val TAG = "超级岛列表模式"
    private const val LIST_MODE_NOTIFICATION_ID = 30000

    fun getNotificationId(): Int = LIST_MODE_NOTIFICATION_ID

    fun isMediaType(paramV2Raw: String?): Boolean {
        if (paramV2Raw.isNullOrBlank()) return false
        return try {
            JSONObject(paramV2Raw).optString("business", "") == "media"
        } catch (_: Exception) {
            false
        }
    }

    fun showFloatingListMode(
        context: Context,
        sourceId: String,
        title: String?,
        text: String?,
        paramV2Raw: String?,
        picMap: Map<String, String>?,
        appName: String?,
        isLocked: Boolean,
    ) {
        val isMedia = isMediaType(paramV2Raw)
        SuperIslandListManager.addOrUpdate(
            SuperIslandListManager.ListEntry(
                sourceId = sourceId,
                title = title,
                text = text,
                paramV2Raw = paramV2Raw,
                picMap = picMap,
                appName = appName,
                isLocked = isLocked,
                isMedia = isMedia,
            ),
        )
        scheduleListModeTimeoutFor(sourceId)
        val active = SuperIslandListManager.getActive()
        if (active != null && active.sourceId == sourceId) {
            sendListModeNotification(context, active)
        }
    }

    fun sendListModeNotification(
        context: Context,
        entry: SuperIslandListManager.ListEntry,
        forceRefresh: Boolean = false,
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            runWithErrorHandlingSuspend("发送列表模式通知") {
                val taskVersion = FloatingReplicaMappingManager.nextVersion(entry.sourceId)

                if (SuperIslandListManager.getActive()?.sourceId != entry.sourceId) {
                    return@runWithErrorHandlingSuspend
                }
                val internedPicMap =
                    withContext(Dispatchers.IO) {
                        SuperIslandImageStore.internAll(context, entry.sourceId, entry.picMap)
                    }

                if (SuperIslandListManager.getActive()?.sourceId != entry.sourceId || !FloatingReplicaMappingManager.isLatestVersion(entry.sourceId, taskVersion)) {
                    return@runWithErrorHandlingSuspend
                }

                val formattedData =
                    SuperIslandDataFormatter.formatForDisplay(
                        context,
                        entry.paramV2Raw,
                        internedPicMap,
                    )
                val paramV2 = formattedData.paramV2
                val displayTitle =
                    entry.title?.takeIf { it.isNotBlank() }
                        ?: paramV2?.highlightInfo?.title?.takeIf { it.isNotBlank() }
                        ?: paramV2?.baseInfo?.title?.takeIf { it.isNotBlank() }
                val displayText =
                    entry.text?.takeIf { it.isNotBlank() }
                        ?: paramV2?.highlightInfo?.content?.takeIf { it.isNotBlank() }
                        ?: paramV2?.baseInfo?.content?.takeIf { it.isNotBlank() }
                val isProgressType = SuperIslandDataFormatter.isProgressType(paramV2)

                // 注入模式：超级岛模式优先于 Live Updates 模式（对齐媒体类型的既有分流范式）。
                // 超级岛模式下，即便含 progressInfo 也走超级岛通道；
                // 仅在「Live Updates 注入且非超级岛」时保留现有 Live Updates 通道。
                val superIslandMode = SuperIslandConfigUtils.isSuperIslandSpecInjectionEnabled(context)
                val liveUpdatesMode = SuperIslandConfigUtils.isLiveUpdatesSpecInjectionEnabled(context)
                val injectionModeOrdinal = SuperIslandConfigUtils.getSpecInjectionMode(context).ordinal

                // 注入模式变化时先取消旧通知并清理旧映射，避免切换后旧通道通知残留
                FloatingReplicaMappingManager.migrateInjectionModeIfChanged(context, entry.sourceId, injectionModeOrdinal)

                // 内容与上次成功发出的通知一致时，跳过系统通知刷新（不调用 notify），仅重置下方撤回计时器；
                // 切换/移除后展示下一条（forceRefresh）时必须强制刷新，避免通知内容停留旧条目。
                // 指纹包含注入模式：模式变化时指纹随之变化，不会被误判为「内容无变更」。
                val fingerprint =
                    FloatingReplicaMappingManager.computeNotificationFingerprint(
                        displayTitle,
                        displayText,
                        formattedData.paramV2Raw,
                        formattedData.resolvedPicMap,
                        injectionModeOrdinal,
                    )
                val previousNotificationIds = FloatingReplicaMappingManager.getNotificationIdsBySourceId(entry.sourceId)
                val canSkipRefresh =
                    !forceRefresh &&
                        !previousNotificationIds.isNullOrEmpty() &&
                        FloatingReplicaMappingManager.isAnyNotificationActive(context, previousNotificationIds) &&
                        fingerprint == FloatingReplicaMappingManager.getNotificationFingerprint(entry.sourceId)

                if (canSkipRefresh) {
                    Logger.i(TAG, "超级岛: 内容无变更，跳过系统通知刷新，仅重置撤回计时器: sourceId=${entry.sourceId}")
                } else if (liveUpdatesMode && !superIslandMode && isProgressType && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    LiveUpdatesNotificationManager.initialize(context)
                    val success =
                        LiveUpdatesNotificationManager.showLiveUpdate(
                            entry.sourceId,
                            displayTitle,
                            displayText,
                            entry.appName,
                            formattedData,
                            overrideNotificationId = LIST_MODE_NOTIFICATION_ID,
                        )
                    if (success) {
                        FloatingReplicaMappingManager.putNotificationId(entry.sourceId, LIST_MODE_NOTIFICATION_ID)
                        FloatingReplicaMappingManager.addSourceIdMapping(entry.sourceId, entry.sourceId, LIST_MODE_NOTIFICATION_ID)
                        FloatingReplicaMappingManager.setNotificationFingerprint(entry.sourceId, fingerprint)
                    } else {
                        FloatingReplicaMappingManager.removeNotificationFingerprint(entry.sourceId)
                    }
                } else {
                    val notificationId =
                        NotificationGenerator.sendReplicaNotification(
                            context,
                            key = entry.sourceId,
                            title = displayTitle,
                            text = displayText,
                            appName = entry.appName,
                            paramV2 = paramV2,
                            paramV2Raw = formattedData.paramV2Raw,
                            picMap = formattedData.resolvedPicMap,
                            sourceId = entry.sourceId,
                            floatingWindowManager = FloatingReplicaWindowManager.getFloatingWindowManager(),
                            overrideNotificationId = LIST_MODE_NOTIFICATION_ID,
                        )
                    if (notificationId != null) {
                        FloatingReplicaMappingManager.addSourceIdMapping(entry.sourceId, entry.sourceId, notificationId)
                        // 仅在确认发出成功后记录指纹，失败时留空以便下次保活包重试
                        FloatingReplicaMappingManager.setNotificationFingerprint(entry.sourceId, fingerprint)
                    }
                }
                scheduleListModeTimeoutFor(entry.sourceId)
            }
        }
    }

    fun scheduleListModeTimeoutFor(sourceId: String) {
        FloatingReplicaMappingManager.cancelTimeoutJob(sourceId)
        val job =
            CoroutineScope(Dispatchers.Main).launch {
                delay(30_000L)
                runWithErrorHandling("列表模式超时移除") {
                    FloatingReplicaWindowManager.dismissBySourceInternal(sourceId, FloatingWindowManager.RemovalReason.TIMEOUT)
                }
            }
        FloatingReplicaMappingManager.setTimeoutJob(sourceId, job)
    }

    fun switchNotificationInList(context: Context) {
        val next = SuperIslandListManager.switchNext()
        if (next != null) {
            Toast
                .makeText(context, "切换", Toast.LENGTH_SHORT)
                .show()
            sendListModeNotification(context, next, forceRefresh = true)
        }
    }

    fun dismissFromList(
        context: Context,
        sourceId: String,
    ) {
        FloatingReplicaMappingManager.removeSourceIdMappings(sourceId)
        val next = SuperIslandListManager.remove(sourceId)
        if (next != null) {
            sendListModeNotification(context, next, forceRefresh = true)
        } else {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.cancel(LIST_MODE_NOTIFICATION_ID)
        }
    }

    fun closeListModeNotification(
        context: Context,
        notificationId: Int,
    ) {
        if (notificationId == LIST_MODE_NOTIFICATION_ID) {
            val active = SuperIslandListManager.getActive()
            if (active != null) {
                FloatingReplicaWindowManager.dismissBySourceInternal(active.sourceId, FloatingWindowManager.RemovalReason.MANUAL)
            }
        } else {
            Logger.i(TAG, "超级岛: 列表模式下关闭非列表通知，notificationId=$notificationId")
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
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
}
