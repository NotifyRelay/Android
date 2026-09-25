package com.xzyht.notifyrelay.feature.notification.superisland.intent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.contract.SuperIslandActions
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.notification.NotificationGenerator
import com.xzyht.notifyrelay.feature.notification.superisland.receiver.NotificationBroadcastReceiver

/**
 * 超级岛通知的**意图与渠道工厂**（唯一来源）。
 *
 * 由原 `ReplicaIntentFactory` + `LiveUpdatesIntentFactory` + `SuperIslandConfigUtils.createDeletePendingIntent`
 * 合并而来（P2-5）：
 * - 复刻通道与 Live Updates 通道原先各有一份「删除意图」，且都转调
 *   `SuperIslandConfigUtils.createDeletePendingIntent`——配置类因此越权承担了意图构造；
 * - `needClickIntent` 的判定原先散落三处（本工厂、`LiveUpdatesNotificationManager`、
 *   `LiveUpdatesIconLoader`），现统一为 [needClickIntent]。
 *
 * 纯构造、无状态。action 契约取自 [SuperIslandActions]，与消费侧
 * [NotificationBroadcastReceiver] 共用同一常量。
 *
 * 本文件原在 `SuperIslandConfigUtils` 中的 `createDeletePendingIntent` 已迁移至此，
 * 配置类回归纯配置读写。
 */
object NotificationIntentFactory {
    /**
     * 计算点击/删除意图所需的条件标志位。
     *
     * 判定口径（与拆分前逐字一致）：浮窗开启 或 列表模式（列表模式仅在浮窗关闭时有效）。
     * 这是本工程唯一一处判定，[com.xzyht.notifyrelay.feature.notification.superisland.notification.LiveUpdatesNotificationManager]
     * 与 [com.xzyht.notifyrelay.feature.notification.superisland.notification.LiveUpdatesIconLoader] 均调用本方法。
     */
    fun needClickIntent(context: Context): Boolean =
        SuperIslandConfigUtils.isFloatingWindowEnabled(context) ||
            SuperIslandConfigUtils.isNotificationListMode(context)

    /**
     * 创建并注册「超级岛复刻」通知渠道（幂等）
     */
    fun ensureChannel(
        context: Context,
        notificationManager: NotificationManager,
    ) {
        // 统一使用"超级岛复刻"通知渠道
        val channel =
            NotificationChannel(
                NotificationGenerator.NOTIFICATION_CHANNEL_ID,
                "超级岛复刻",
                NotificationManager.IMPORTANCE_HIGH,
            )
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 创建通知移除时的删除意图（用于用户移除通知时关闭浮窗）。
     *
     * 广播 action 取自 [SuperIslandActions.CLOSE_NOTIFICATION]，与消费侧共用同一常量。
     */
    fun createDeleteIntent(
        context: Context,
        notificationId: Int,
    ): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, NotificationBroadcastReceiver::class.java)
                .putExtra("notificationId", notificationId)
                .setAction(SuperIslandActions.CLOSE_NOTIFICATION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * 创建通知点击意图（用于用户点击通知时切换浮窗或切换列表）。
     *
     * 两种调用形态共用本方法，参数差异即原实现差异：
     * - 复刻通道会带上 `picMap`（构造 [Intent] 后在 [createPendingContentIntent] 前填充）；
     * - Live Updates 通道的 `paramV2Raw` / `appName` 在图标异步更新路径中为 null。
     *
     * @param key 浮窗条目 key；`paramV2Raw` 为空时回退到该条目上记录的 raw（复刻通道行为）
     */
    fun createContentIntent(
        context: Context,
        key: String,
        title: String?,
        text: String?,
        appName: String?,
        paramV2Raw: String?,
        picMap: Map<String, String>?,
        floatingWindowManager: FloatingWindowManager?,
        sourceId: String,
    ): Intent =
        Intent(context, NotificationBroadcastReceiver::class.java).apply {
            action = SuperIslandActions.TOGGLE_FLOATING
            putExtra("sourceId", sourceId)
            putExtra("title", title)
            putExtra("text", text)
            putExtra("appName", appName)

            // 优先使用传入的paramV2Raw参数，其次从entry中获取
            val entry = floatingWindowManager?.getEntry(key)
            val rawParamV2 = paramV2Raw ?: entry?.paramV2Raw
            if (!rawParamV2.isNullOrBlank()) {
                putExtra("paramV2Raw", rawParamV2)
            }

            // 传入图片映射
            if (!picMap.isNullOrEmpty()) {
                val bundle = Bundle()
                picMap.forEach { (k, v) -> bundle.putString(k, v) }
                putExtra("picMap", bundle)
            }
        }

    /**
     * 由点击意图构造 [PendingIntent]。
     */
    fun createPendingContentIntent(
        context: Context,
        notificationId: Int,
        contentIntent: Intent?,
    ): PendingIntent? =
        if (contentIntent != null) {
            PendingIntent.getBroadcast(
                context,
                notificationId,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            null
        }

    /**
     * 直接构造点击 [PendingIntent]（Live Updates 通道使用：无需回退查条目、无 picMap）。
     */
    fun createPendingContentIntent(
        context: Context,
        notificationId: Int,
        sourceId: String,
        title: String?,
        text: String?,
        appName: String?,
        paramV2Raw: String?,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, NotificationBroadcastReceiver::class.java)
                .putExtra("sourceId", sourceId)
                .putExtra("title", title)
                .putExtra("text", text)
                .putExtra("appName", appName)
                .putExtra("paramV2Raw", paramV2Raw)
                .setAction(SuperIslandActions.TOGGLE_FLOATING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
