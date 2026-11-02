package com.xzyht.notifyrelay.feature.notification.superisland.recovery

import android.app.NotificationManager
import android.content.Context
import com.xzyht.notifyrelay.feature.notification.superisland.notification.NotificationGenerator
import notifyrelay.base.log.Logger

/**
 * 进程启动时清理上一次运行残留的超级岛通知。
 *
 * 超级岛的展示状态（sourceId 映射、通知 id、内容指纹、超时任务）全部保存在内存中，
 * 进程异常终止后这些状态随之丢失，但已发出的系统通知仍留在通知栏：
 * 既无法按 sourceId 撤回（映射已丢），也无法被后续保活包刷新（指纹已丢，
 * 且 Rust 侧的合并状态与平台侧不再一致）。
 *
 * 因此启动时按通知渠道扫描本应用的活动通知，取消全部超级岛通道通知，
 * 让下一次内容到达时从干净状态重新建立展示。
 *
 * 不持久化展示状态：恢复只需「清掉残留」，不需要还原上一次的内容。
 */
object SuperIslandStartupRecovery {
    private const val TAG = "超级岛恢复"

    /**
     * 取消本应用在超级岛渠道上的全部活动通知。
     *
     * @return 取消的通知数量
     */
    fun clearStaleNotifications(context: Context): Int {
        val appContext = context.applicationContext
        return try {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val stale =
                manager.activeNotifications.filter { sbn ->
                    sbn.packageName == appContext.packageName &&
                        sbn.notification.channelId == NotificationGenerator.NOTIFICATION_CHANNEL_ID
                }
            stale.forEach { sbn ->
                runCatching { manager.cancel(sbn.id) }
            }
            if (stale.isNotEmpty()) {
                Logger.i(TAG, "清理上次运行残留的超级岛通知: " + stale.size + " 条")
            }
            stale.size
        } catch (e: Exception) {
            Logger.w(TAG, "清理残留超级岛通知失败", e)
            0
        }
    }
}
