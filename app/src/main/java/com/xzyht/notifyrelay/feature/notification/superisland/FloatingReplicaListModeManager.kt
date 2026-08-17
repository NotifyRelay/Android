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

object FloatingReplicaListModeManager {
    private const val TAG = "超级岛列表模式"
    private const val LIST_MODE_NOTIFICATION_ID = 30000

    fun getNotificationId(): Int = LIST_MODE_NOTIFICATION_ID

    fun isMediaType(paramV2Raw: String?): Boolean {
        if (paramV2Raw.isNullOrBlank()) return false
        return try {
            org.json.JSONObject(paramV2Raw).optString("business", "") == "media"
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
        SuperislandListManager.addOrUpdate(
            SuperislandListManager.ListEntry(
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
        val active = SuperislandListManager.getActive()
        if (active != null && active.sourceId == sourceId) {
            sendListModeNotification(context, active)
        }
    }

    fun sendListModeNotification(
        context: Context,
        entry: SuperislandListManager.ListEntry,
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            runWithErrorHandlingSuspend("发送列表模式通知") {
                val taskVersion = FloatingReplicaMappingManager.nextVersion(entry.sourceId)

                if (SuperislandListManager.getActive()?.sourceId != entry.sourceId) {
                    return@runWithErrorHandlingSuspend
                }
                val internedPicMap =
                    withContext(Dispatchers.IO) {
                        SuperIslandImageStore.internAll(context, entry.sourceId, entry.picMap)
                    }

                if (SuperislandListManager.getActive()?.sourceId != entry.sourceId || !FloatingReplicaMappingManager.isLatestVersion(entry.sourceId, taskVersion)) {
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
                if (isProgressType && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.BAKLAVA) {
                    LiveUpdatesNotificationManager.initialize(context)
                    LiveUpdatesNotificationManager.showLiveUpdate(
                        entry.sourceId,
                        displayTitle,
                        displayText,
                        entry.appName,
                        formattedData,
                        overrideNotificationId = LIST_MODE_NOTIFICATION_ID,
                    )
                    FloatingReplicaMappingManager.putNotificationId(entry.sourceId, LIST_MODE_NOTIFICATION_ID)
                    FloatingReplicaMappingManager.addSourceIdMapping(entry.sourceId, entry.sourceId, LIST_MODE_NOTIFICATION_ID)
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
                    if (notificationId != null) FloatingReplicaMappingManager.addSourceIdMapping(entry.sourceId, entry.sourceId, notificationId)
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
        val next = SuperislandListManager.switchNext()
        if (next != null) {
            android.widget.Toast
                .makeText(context, "切换", android.widget.Toast.LENGTH_SHORT)
                .show()
            sendListModeNotification(context, next)
        }
    }

    fun dismissFromList(
        context: Context,
        sourceId: String,
    ) {
        FloatingReplicaMappingManager.removeSourceIdMappings(sourceId)
        val next = SuperislandListManager.remove(sourceId)
        if (next != null) {
            sendListModeNotification(context, next)
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
            val active = SuperislandListManager.getActive()
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
