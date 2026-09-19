package com.xzyht.notifyrelay.feature.notification.superisland.notification

import android.R
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.xzyht.notifyrelay.feature.notification.superisland.notification.NotificationGenerator.NOTIFICATION_CHANNEL_ID
import github.xzynine.superislandui.common.CapsuleScrollManager
import notifyrelay.base.util.Logger

/**
 * 复刻通知胶囊文本的滚动更新管理。
 *
 * 负责在 Live Updates 模式下周期性刷新胶囊滚动文本并保留小图标。
 * [ReplicaIconCache] 中的图标缓存由本类与 [ReplicaSmallIconInjector] 共用。
 */
internal object ReplicaScrollUpdater {
    private const val TAG = "超级岛通知生成"

    private val mainHandler = Handler(Looper.getMainLooper())

    // ConcurrentHashMap：本 map 会被主线程 Handler 回调与调用方线程（setup/stop/clear）并发访问，
    // 普通 MutableMap 的 forEach 期间修改会抛 ConcurrentModificationException（如 clearAllScrollUpdates）。
    private val scrollRunnable = java.util.concurrent.ConcurrentHashMap<String, Runnable>()

    /**
     * 设置滚动更新
     */
    fun setupScrollUpdate(
        key: String,
        scrollKey: String,
        capsuleText: String,
        context: Context,
        notificationId: Int,
        originalBuilder: NotificationCompat.Builder,
        notificationManager: NotificationManager,
        progressStyle: NotificationCompat.ProgressStyle? = null,
    ) {
        // 移除旧的滚动Runnable
        scrollRunnable.remove(key)?.let {
            mainHandler.removeCallbacks(it)
        }

        // 创建新的滚动Runnable
        val runnable =
            Runnable {
                try {
                    // 检查是否需要更新通知
                    if (!CapsuleScrollManager.shouldUpdateNotification(scrollKey)) {
                        return@Runnable
                    }

                    // 获取当前应该显示的内容
                    val displayText = CapsuleScrollManager.getCurrentDisplayText(scrollKey, capsuleText)

                    // 构建原始通知以获取其属性
                    val originalNotification = originalBuilder.build()

                    // 获取原始通知的标题和内容
                    val contentTitle = originalNotification.extras.getString("android.title")
                    val contentText = originalNotification.extras.getString("android.text")

                    // 更新通知（必须设置 smallIcon，否则会抛出 IllegalArgumentException）
                    val updatedBuilder =
                        NotificationCompat
                            .Builder(context, NOTIFICATION_CHANNEL_ID)
                            .setContentTitle(contentTitle ?: "")
                            .setContentText(contentText ?: "")
                            .setSmallIcon(R.drawable.stat_notify_more)
                            .setAutoCancel(false)
                            .setOngoing(true)
                            .setPriority(NotificationCompat.PRIORITY_MAX)
                            .setShowWhen(false)
                            .setWhen(System.currentTimeMillis())
                            .setOnlyAlertOnce(true)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                            .setRequestPromotedOngoing(true)
                            .setShortCriticalText(displayText)
                            .apply {
                                // 保留 ProgressStyle，避免滚动更新后丢失 Live Updates（超级岛）渲染
                                progressStyle?.let { setStyle(it) }
                            }

                    // 复制extras
                    val updatedNotification = updatedBuilder.build()
                    updatedNotification.extras.putAll(originalNotification.extras)

                    // putAll 会用原通知 extras 中的旧值覆盖刚设置的滚动文本（API 36+ 平台的
                    // setShortCriticalText 就写入 extras 的 android.shortCriticalText），
                    // 故必须在复制之后重新写回本次要显示的滚动文本，否则滚动不生效。
                    updatedNotification.extras.putString(NotificationCompat.EXTRA_SHORT_CRITICAL_TEXT, displayText)

                    // 恢复之前缓存的小图标，避免滚动更新时丢失实际意义图标
                    val cachedIcon = ReplicaIconCache.get(key)
                    if (cachedIcon != null) {
                        try {
                            val field = Notification::class.java.getDeclaredField("mSmallIcon")
                            field.isAccessible = true
                            field.set(updatedNotification, cachedIcon)
                        } catch (e: Exception) {
                            Logger.w(TAG, "滚动更新: 恢复小图标失败: ${e.message}")
                        }
                    }

                    // 发送更新后的通知
                    notificationManager.notify(notificationId, updatedNotification)

                    // 继续调度下一次更新
                    val delay = CapsuleScrollManager.getScrollDelay(scrollKey)
                    scrollRunnable[key]?.let { mainHandler.postDelayed(it, delay) }
                } catch (e: Exception) {
                    Logger.e(TAG, "滚动更新失败", e)
                }
            }

        // 存储Runnable
        scrollRunnable[key] = runnable

        // 调度第一次更新，初始延迟0，确保滚动直接开始
        mainHandler.postDelayed(runnable, 0)
    }

    /**
     * 共享通知ID模式下（列表模式），清理除 [keepKey] 以外的所有滚动任务，避免冲突。
     */
    fun stopOtherScrollUpdatesExcept(keepKey: String) {
        scrollRunnable.keys.toList().forEach { oldKey ->
            if (oldKey != keepKey) {
                stopScrollUpdate(oldKey)
            }
        }
    }

    /**
     * 停止滚动更新
     */
    fun stopScrollUpdate(key: String) {
        scrollRunnable.remove(key)?.let {
            mainHandler.removeCallbacks(it)
        }
        ReplicaIconCache.remove(key)
        CapsuleScrollManager.resetScrollState("${key}_scroll")
    }

    /**
     * 清理所有滚动更新
     */
    fun clearAllScrollUpdates() {
        scrollRunnable.forEach {
            mainHandler.removeCallbacks(it.value)
        }
        scrollRunnable.clear()
        ReplicaIconCache.clear()
        CapsuleScrollManager.clearAll()
    }
}
