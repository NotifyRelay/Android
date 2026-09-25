package com.xzyht.notifyrelay.feature.notification.superisland.list

import android.app.NotificationManager
import android.content.Context
import android.widget.Toast
import com.xzyht.notifyrelay.feature.notification.service.ListenerForegroundController
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.formatter.SuperIslandDataFormatter
import com.xzyht.notifyrelay.feature.notification.superisland.pipeline.SuperIslandDisplayPipeline
import com.xzyht.notifyrelay.feature.notification.superisland.replica.FloatingReplicaWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.replica.ReplicaStateStore
import com.xzyht.notifyrelay.feature.notification.superisland.replica.runReplicaCatching
import com.xzyht.notifyrelay.feature.notification.superisland.replica.runReplicaCatchingSuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object FloatingReplicaListModeManager {
    private const val TAG = "超级岛列表模式"
    private const val LIST_MODE_NOTIFICATION_ID = SuperIslandDisplayPipeline.LIST_MODE_NOTIFICATION_ID

    fun getNotificationId(): Int = LIST_MODE_NOTIFICATION_ID

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
        // 媒体判定统一走 SuperIslandDataFormatter.isMediaType（含 raw 字符串兜底）
        val isMedia = SuperIslandDataFormatter.isMediaType(null, paramV2Raw)
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
        // 列表条目增减不在设备状态流/网络回调覆盖范围内，需主动刷新前台常驻通知的「可切换」提示
        ListenerForegroundController.onSuperIslandListChanged()
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
            runReplicaCatchingSuspend(TAG, "发送列表模式通知") {
                // 展示管线（三通道共享）：
                // - preGuard = 「列表 active 条目仍是本条目」（等价于原实现的两次 active 检查）；
                // - 无 extraGuard（原实现无 isSourceRecentlyClosed 复检）；
                // - overrideNotificationId 固定为列表聚合 ID；超时调度留在本薄壳内。
                val dispatched =
                    SuperIslandDisplayPipeline.dispatch(
                        context = context,
                        request =
                            SuperIslandDisplayPipeline.DisplayRequest(
                                sourceId = entry.sourceId,
                                title = entry.title,
                                text = entry.text,
                                paramV2Raw = entry.paramV2Raw,
                                picMap = entry.picMap,
                                appName = entry.appName,
                                isLocked = entry.isLocked,
                                channel = SuperIslandDisplayPipeline.Channel.LIST,
                                tag = TAG,
                                forceRefresh = forceRefresh,
                                titleFallback = null,
                                textFallback = null,
                                preGuard = { SuperIslandListManager.getActive()?.sourceId == entry.sourceId },
                                skipRefreshMessage = "内容无变更，跳过系统通知刷新，仅重置撤回计时器",
                                // 列表模式：映射登记与指纹记录都只在发送成功时进行（与原实现一致）
                                registerMappingBeforeSend = false,
                                registerMappingOnSendFailure = false,
                                registerLiveUpdateMappingRegardlessOfSuccess = false,
                                // 列表模式原实现的 Live Updates 分支未包裹异常（异常冒泡到外层）
                                liveUpdateErrorsPropagate = true,
                            ),
                    )

                if (!dispatched) return@runReplicaCatchingSuspend

                scheduleListModeTimeoutFor(entry.sourceId)
            }
        }
    }

    fun scheduleListModeTimeoutFor(sourceId: String) {
        ReplicaStateStore.cancelTimeoutJob(sourceId)
        val job =
            CoroutineScope(Dispatchers.Main).launch {
                delay(30_000L)
                runReplicaCatching(TAG, "列表模式超时移除") {
                    FloatingReplicaWindowManager.dismissBySourceInternal(sourceId, FloatingWindowManager.RemovalReason.TIMEOUT)
                }
            }
        ReplicaStateStore.setTimeoutJob(sourceId, job)
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
        ReplicaStateStore.removeSourceIdMappings(sourceId)
        val next = SuperIslandListManager.remove(sourceId)
        ListenerForegroundController.onSuperIslandListChanged()
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
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
        }
    }
}
