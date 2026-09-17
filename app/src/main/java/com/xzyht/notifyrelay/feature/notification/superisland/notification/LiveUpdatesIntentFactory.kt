package com.xzyht.notifyrelay.feature.notification.superisland.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.receiver.NotificationBroadcastReceiver

/**
 * 超级岛进度通知的意图工厂。
 *
 * 由 [LiveUpdatesNotificationManager] 拆分而来：原先在 `showLiveUpdate` 与
 * `updateNotificationWithAllIcons` 中各有一份完全相同的意图构造代码，现统一到此处。
 *
 * [ACTION_TOGGLE_FLOATING] 必须与 [NotificationBroadcastReceiver] 中处理的字符串完全一致，
 * 否则点击通知将无法切换浮窗/列表。
 */
internal object LiveUpdatesIntentFactory {
    /** 点击通知切换浮窗/列表的广播 action，与 NotificationBroadcastReceiver 保持一致 */
    internal const val ACTION_TOGGLE_FLOATING = "com.xzyht.notifyrelay.ACTION_TOGGLE_FLOATING"

    /**
     * 创建通知移除时的删除意图（用于用户移除通知时关闭浮窗）。
     */
    internal fun createDeleteIntent(
        context: Context,
        notificationId: Int,
    ): PendingIntent? = SuperIslandConfigUtils.createDeletePendingIntent(context, notificationId)

    /**
     * 创建通知点击意图（用于用户点击通知时切换浮窗或切换列表）。
     *
     * 注意：[appName] 与 [paramV2Raw] 在图标异步更新路径中无对应值（传 null），
     * 与原实现一致：[NotificationBroadcastReceiver] 通过 `getStringExtra` 读取，
     * 缺失或为 null 均返回 null。
     */
    internal fun createContentIntent(
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
                .setAction(ACTION_TOGGLE_FLOATING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
