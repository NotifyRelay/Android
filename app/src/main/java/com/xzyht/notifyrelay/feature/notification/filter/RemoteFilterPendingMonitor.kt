package com.xzyht.notifyrelay.feature.notification.filter

import android.app.NotificationManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import notifyrelay.base.util.Logger
import kotlin.time.Duration.Companion.milliseconds

/**
 * 待监控的通知撤回队列 + 超时监控协程。
 * 持有 pendingNotifications 列表，负责将超时的待撤回通知写入去重缓存并移除。
 * 监控协程（while + delay(100ms) 循环）生命周期由 pendingNotifications
 * 空/非空驱动：调用 addPendingNotification 触发启动，列表为空时退出，再次 add 时重入。
 *
 * 防重入：同时最多只存在 **一个** 监控协程（原实现每次 add 都无条件 launch，
 * 快速连续 add N 条会启 N 个协程同时轮询）。[monitoringJob] 与列表同锁保护，
 * 避免"循环判空退出"与"新条目入队"竞争导致漏监控。
 */
internal class RemoteFilterPendingMonitor(
    private val scope: CoroutineScope,
    private val onDedupCache: (title: String, text: String) -> Unit,
    private val onClearExpiredPlaceholders: () -> Unit,
) {
    data class PendingNotification(
        val notifyId: Int,
        val title: String,
        val text: String,
        val packageName: String,
        val sendTime: Long,
        val context: Context,
    )

    private val pendingNotifications = mutableListOf<PendingNotification>()

    // 当前监控协程；与 pendingNotifications 同锁保护，保证同时至多一个
    private var monitoringJob: Job? = null

    /** 添加待监控的通知；只有去重开启时才添加，并触发监控协程。 */
    fun addPendingNotification(
        notifyId: Int,
        title: String,
        text: String,
        packageName: String,
        context: Context,
    ) {
        synchronized(pendingNotifications) {
            pendingNotifications.add(
                PendingNotification(
                    notifyId = notifyId,
                    title = title,
                    text = text,
                    packageName = packageName,
                    sendTime = System.currentTimeMillis(),
                    context = context,
                ),
            )
            startNotificationMonitoringLocked()
        }
    }

    /** 撤回匹配的待监控通知（由被动入队命中时调用，按标准化标题匹配）。返回被移除的通知（供调用方写去重缓存）。 */
    fun cancelMatching(
        normalizedTitle: String,
        pendingText: String,
        packageName: String,
    ): List<PendingNotification> {
        synchronized(pendingNotifications) {
            val matches =
                pendingNotifications.filter { pending ->
                    normalizeTitle(pending.title) == normalizedTitle && pending.text == pendingText && pending.packageName == packageName
                }
            matches.forEach { matched ->
                try {
                    cancelNotification(matched.notifyId, matched.context)
                    // Logger.d("智能去重", "被动撤回成功 - 通知ID:${matched.notifyId}, 标题:${matched.title}")
                } catch (e: Exception) {
                    Logger.e("智能去重", "被动撤回失败 - 通知ID:${matched.notifyId}", e)
                }
            }
            if (matches.isNotEmpty()) pendingNotifications.removeAll(matches)
            return matches
        }
    }

    /** 撤回匹配的待监控通知（命中 10 秒去重缓存时调用，按原始标题/文本匹配，与缓存写入口径一致）。返回被移除的通知。 */
    fun cancelMatchingRaw(
        rawTitle: String,
        rawText: String,
    ): List<PendingNotification> {
        synchronized(pendingNotifications) {
            val matches =
                pendingNotifications.filter { pending ->
                    pending.title == rawTitle && pending.text == rawText
                }
            matches.forEach { matched ->
                try {
                    cancelNotification(matched.notifyId, matched.context)
                    // Logger.d("智能去重", "被动撤回成功 - 通知ID:${matched.notifyId}, 标题:${matched.title}")
                } catch (e: Exception) {
                    Logger.e("智能去重", "被动撤回失败 - 通知ID:${matched.notifyId}", e)
                }
            }
            if (matches.isNotEmpty()) pendingNotifications.removeAll(matches)
            return matches
        }
    }

    fun clear() {
        synchronized(pendingNotifications) { pendingNotifications.clear() }
    }

    /** 撤回通知 */
    private fun cancelNotification(
        notifyId: Int,
        context: Context,
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notifyId)
            // Logger.d("智能去重", "已撤回通知 - 通知ID:$notifyId")
        } catch (e: Exception) {
            Logger.e("智能去重", "撤回通知失败 - 通知ID:$notifyId, 错误:${e.message}")
        }
    }

    /**
     * 启动通知监控协程（须已持有 [pendingNotifications] 监视器）。
     * 已有在跑的协程时直接返回，实现防重入。
     */
    private fun startNotificationMonitoringLocked() {
        if (monitoringJob?.isActive == true) return
        monitoringJob =
            scope.launch {
                while (true) {
                    val now = System.currentTimeMillis()
                    val toRemove = mutableListOf<PendingNotification>()
                    val shouldContinue: Boolean

                    synchronized(pendingNotifications) {
                        for (pending in pendingNotifications) {
                            // 仅处理监控超时逻辑：我们改为被动匹配（由本机历史入队触发匹配），减少频繁IO读取历史
                            if (now - pending.sendTime > 15_000) {
                                // Logger.d("智能去重", "监控超时移除 - 包名:${pending.packageName}, 标题:${pending.title}, 通知ID:${pending.notifyId}, 监控时长:${now - pending.sendTime}ms")
                                toRemove.add(pending)
                            }
                        }

                        // 移除已处理的待监控通知（toRemove 中元素均已满足超时条件）
                        pendingNotifications.removeAll(toRemove)

                        // 为超时移除的通知添加去重缓存
                        toRemove.forEach { timedOut ->
                            onDedupCache(timedOut.title, timedOut.text)
                            // Logger.d("智能去重", "超时通知添加到缓存 - 标题:${timedOut.title}, 内容:${timedOut.text}")
                        }

                        // 在同一把锁内判空并清空 job 引用：若此时列表非空（含并发新入队）则继续循环；
                        // 为空则退出。二者与 addPendingNotification 的入队同锁，故不会出现"退出瞬间漏启"。
                        shouldContinue = pendingNotifications.isNotEmpty()
                        if (!shouldContinue) {
                            monitoringJob = null
                        }
                    }

                    // 清理过期占位，避免内存泄露（保持原实现语义：每轮循环清理一次，含最后一轮）
                    onClearExpiredPlaceholders()

                    if (!shouldContinue) {
                        // Logger.d("智能去重", "监控协程结束 - 所有通知已处理完成")
                        return@launch
                    }

                    // 避免无待处理任务时忙转（最多损失 100ms 判定精度，15s 撤回窗口不受影响）
                    delay(100.milliseconds)
                }
            }
    }
}
