package com.xzyht.notifyrelay.feature.notification.superisland

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.xzyht.notifyrelay.common.core.util.Logger
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.model.ParamV2
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.model.parseParamV2

@RequiresApi(36)
object LiveUpdatesNotificationManager {
    private const val TAG = "LiveUpdatesNotificationManager"
    const val CHANNEL_ID = "live_updates_channel"
    private const val CHANNEL_NAME = "超级岛Live Updates"
    private const val NOTIFICATION_BASE_ID = 10000
    private lateinit var notificationManager: NotificationManager
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    fun showLiveUpdate(
        sourceId: String,
        title: String?,
        text: String?,
        paramV2Raw: String?,
        appName: String?,
        isLocked: Boolean
    ) {
        if (Build.VERSION.SDK_INT < 36) {
            Logger.w(TAG, "Live Updates not supported on this Android version")
            return
        }

        try {
            val notificationId = sourceId.hashCode().and(0xffff) + NOTIFICATION_BASE_ID
            val paramV2 = paramV2Raw?.let { parseParamV2(it) }

            val notificationBuilder = buildBaseNotification(sourceId)
                .setContentTitle(title ?: appName ?: "超级岛通知")
                .setContentText(text ?: "")
                .setSmallIcon(android.R.drawable.stat_notify_more)

            // 根据paramV2数据构建不同样式的Live Update
            when {
                paramV2?.progressInfo != null -> {
                    // 进度条样式
                    buildProgressStyleNotification(notificationBuilder, paramV2)
                }
                paramV2?.highlightInfo != null -> {
                    // 强调样式
                    buildHighlightStyleNotification(notificationBuilder, paramV2)
                }
                else -> {
                    // 基础样式
                    buildBasicStyleNotification(notificationBuilder, paramV2)
                }
            }

            // 设置状态栏关键文本
            if (title?.isNotEmpty() == true && title.length <= 7) {
                notificationBuilder.setShortCriticalText(title)
            } else if (appName?.isNotEmpty() == true && appName.length <= 7) {
                notificationBuilder.setShortCriticalText(appName)
            }

            val notification = notificationBuilder.build()
            notificationManager.notify(notificationId, notification)
            Logger.i(TAG, "Sent Live Update notification: $sourceId")
        } catch (e: Exception) {
            Logger.e(TAG, "Error showing Live Update notification: ${e.message}")
        }
    }

    private fun buildBaseNotification(sourceId: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
    }

    private fun buildBasicStyleNotification(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2?
    ): NotificationCompat.Builder {
        return builder.setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(paramV2?.baseInfo?.title ?: builder.build().extras.getString(NotificationCompat.EXTRA_TEXT))
        )
    }

    private fun buildProgressStyleNotification(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2
    ): NotificationCompat.Builder {
        val progressInfo = paramV2.progressInfo ?: return builder
        
        val progressStyle = NotificationCompat.ProgressStyle()
            .setProgress(
                progressInfo.progress,
                100,
                false
            )

        return builder.setStyle(progressStyle)
    }

    private fun buildHighlightStyleNotification(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2
    ): NotificationCompat.Builder {
        val highlightInfo = paramV2.highlightInfo ?: return builder
        
        return builder.setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(highlightInfo.title ?: "")
        )
    }

    fun cancelLiveUpdate(sourceId: String) {
        try {
            val notificationId = sourceId.hashCode().and(0xffff) + NOTIFICATION_BASE_ID
            notificationManager.cancel(notificationId)
            Logger.i(TAG, "Cancelled Live Update notification: $sourceId")
        } catch (e: Exception) {
            Logger.e(TAG, "Error cancelling Live Update notification: ${e.message}")
        }
    }
    
    // 兼容旧方法名
    fun dismissLiveUpdateNotification(sourceId: String) {
        cancelLiveUpdate(sourceId)
    }

    fun cancelAllLiveUpdates() {
        notificationManager.cancelAll()
        Logger.i(TAG, "Cancelled all Live Update notifications")
    }

    fun canUseLiveUpdates(): Boolean {
        return Build.VERSION.SDK_INT >= 36 && 
               notificationManager.canPostPromotedNotifications()
    }

    fun hasPromotableCharacteristics(notification: NotificationCompat.Builder): Boolean {
        return notification.build().hasPromotableCharacteristics()
    }
}