package com.xzyht.notifyrelay.feature.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Handler
import notifyrelay.base.util.Logger

/**
 * 本地高优先级悬浮通知（抽取自 [com.xzyht.notifyrelay.sync.MessageSender]）。
 *
 * 仅做搬移/抽取，逻辑与 [com.xzyht.notifyrelay.sync.MessageSender] 原实现逐行保持一致：
 * - 本地悬浮通知 + 5s 自动取消（主线程依赖：[Handler] postDelayed 不可改）。
 * - 无 JSON 契约。
 */
object HighPriorityNotifier {
    private const val TAG = "MessageSender"

    /**
     * 发送高优先级悬浮通知（用于应用跳转指示）
     * @param context 上下文
     * @param title 通知标题
     * @param text 通知内容
     */
    fun send(
        context: Context,
        title: String?,
        text: String?,
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "notifyrelay_temp"

            // 创建通知渠道（如果不存在）
            if (notificationManager.getNotificationChannel(channelId) == null) {
                val channel =
                    NotificationChannel(channelId, "跳转通知", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "应用内跳转指示通知"
                        enableLights(true)
                        lightColor = Color.BLUE
                        enableVibration(false)
                        setSound(null, null)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                        setShowBadge(false)
                        importance = NotificationManager.IMPORTANCE_HIGH
                        setBypassDnd(true)
                    }
                notificationManager.createNotificationChannel(channel)
            }

            // 构建通知
            val builder =
                Notification.Builder(context, channelId).apply {
                    setContentTitle(title ?: "(无标题)")
                    setContentText(text ?: "(无内容)")
                    setSmallIcon(android.R.drawable.ic_dialog_info)
                    setCategory(Notification.CATEGORY_MESSAGE)
                    setAutoCancel(true)
                    setVisibility(Notification.VISIBILITY_PUBLIC)
                    setOngoing(false)
                }

            // 发送通知，使用当前时间戳作为ID
            val notifyId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            notificationManager.notify(notifyId, builder.build())

            // 5秒后自动销毁通知
            Handler(context.mainLooper).postDelayed({
                notificationManager.cancel(notifyId)
            }, 5000)

            // Logger.d(TAG, "高优先级悬浮通知已发送: $title")
        } catch (e: Exception) {
            Logger.e(TAG, "发送高优先级通知失败", e)
        }
    }
}
