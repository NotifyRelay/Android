package com.xzyht.notifyrelay.feature.notification.superisland.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.notification.NotificationGenerator.NOTIFICATION_CHANNEL_ID
import com.xzyht.notifyrelay.feature.notification.superisland.receiver.NotificationBroadcastReceiver

/**
 * 复刻通知的配置与意图构建。
 *
 * 封装通知渠道创建、点击/删除意图构造（与 [NotificationBroadcastReceiver] 的 action 契约保持一致），
 * 以及浮窗/列表模式判定所需的派生标志位。纯构造、无状态。
 */
internal object ReplicaIntentFactory {
    /**
     * 验证规范信息注入开关状态，确保至少有一种开启
     */
    fun validateSpecInjectionSwitches(context: Context) {
        SuperIslandConfigUtils.validateSpecInjectionSwitches(context)
    }

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
                NOTIFICATION_CHANNEL_ID,
                "超级岛复刻",
                NotificationManager.IMPORTANCE_HIGH,
            )
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 计算点击/删除意图所需的条件标志位。
     */
    data class IntentFlags(
        val needClickIntent: Boolean,
    )

    /**
     * 判定是否需要设置点击意图：浮窗开启 或 列表模式
     */
    fun computeIntentFlags(context: Context): IntentFlags {
        // 检查浮窗功能是否开启
        val floatingWindowEnabled = SuperIslandConfigUtils.isFloatingWindowEnabled(context)
        // 检查列表模式（仅通知 + 切换）
        val notificationListMode = !floatingWindowEnabled && SuperIslandConfigUtils.isNotificationListMode(context)
        // 需要设置 click intent 的条件：浮窗开启 或 列表模式
        val needClickIntent = floatingWindowEnabled || notificationListMode
        return IntentFlags(needClickIntent)
    }

    /**
     * 构造点击意图（点击通知时切换浮窗或切换列表）。
     * 仅在 [needClickIntent] 为 true 时返回非空意图。
     */
    fun createContentIntent(
        context: Context,
        key: String,
        title: String?,
        text: String?,
        appName: String?,
        paramV2Raw: String?,
        picMap: Map<String, String>?,
        floatingWindowManager: FloatingWindowManager,
        needClickIntent: Boolean,
        sourceId: String,
    ): Intent? =
        if (needClickIntent) {
            Intent(context, NotificationBroadcastReceiver::class.java).apply {
                action = "com.xzyht.notifyrelay.ACTION_TOGGLE_FLOATING"
                putExtra("sourceId", sourceId)
                putExtra("title", title)
                putExtra("text", text)
                putExtra("appName", appName)

                // 优先使用传入的paramV2Raw参数，其次从entry中获取
                val entry = floatingWindowManager.getEntry(key)
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
        } else {
            null
        }

    /**
     * 由点击意图构造 [PendingIntent]。
     */
    fun createPendingContentIntent(
        context: Context,
        notificationId: Int,
        contentIntent: Intent?,
        needClickIntent: Boolean,
    ): PendingIntent? =
        if (needClickIntent && contentIntent != null) {
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
     * 构造删除意图（移除通知时关闭浮窗）。
     */
    fun createDeleteIntent(
        context: Context,
        notificationId: Int,
        needClickIntent: Boolean,
    ): PendingIntent? =
        if (needClickIntent) {
            SuperIslandConfigUtils.createDeletePendingIntent(context, notificationId)
        } else {
            null
        }
}
