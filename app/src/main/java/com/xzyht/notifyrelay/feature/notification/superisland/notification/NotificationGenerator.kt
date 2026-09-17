package com.xzyht.notifyrelay.feature.notification.superisland.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.replica.FloatingReplicaMappingManager
import github.xzynine.superislandui.model.core.ParamV2
import kotlinx.coroutines.CancellationException
import notifyrelay.base.util.Logger

/**
 * 通知生成器，负责处理超级岛通知的生成和注入
 *
 * 耦合逻辑说明:
 * 1. 浮窗功能与通知点击事件的耦合:
 *    - 当浮窗功能开启时，为通知设置点击意图和删除意图
 *    - 点击意图 action = com.xzyht.notifyrelay.ACTION_TOGGLE_FLOATING
 *    - 删除意图 action = com.xzyht.notifyrelay.ACTION_CLOSE_NOTIFICATION
 *    - 这些意图会触发 NotificationBroadcastReceiver 中的相应处理逻辑
 *
 * 2. 通知与浮窗的去耦合:
 *    - 通过 SUPER_ISLAND_FLOATING_WINDOW_KEY 开关控制浮窗功能
 *    - 浮窗功能关闭时，不设置与浮窗关联的通知点击和关闭意图
 *    - 浮窗功能关闭时，仅创建基础通知，不添加与浮窗相关的功能
 *
 * 拆分后：配置/意图由 [ReplicaIntentFactory] 负责，媒体分支由 [MediaReplicaNotifier] 负责，
 * 非媒体分支由 [GeneralReplicaNotifier] 负责，滚动更新由 [ReplicaScrollUpdater] 负责，
 * 小图标注入由 [ReplicaSmallIconInjector] 负责，共享缓存由 [ReplicaIconCache] 负责。
 */
object NotificationGenerator {
    private const val TAG = "超级岛通知生成"

    // 通知渠道ID
    internal const val NOTIFICATION_CHANNEL_ID = "super_island_replica"

    // 通知ID基础值
    private const val NOTIFICATION_BASE_ID = 20000

    /**
     * 停止滚动更新（委托给 [ReplicaScrollUpdater]）
     */
    fun stopScrollUpdate(key: String) {
        ReplicaScrollUpdater.stopScrollUpdate(key)
    }

    /**
     * 清理所有滚动更新（委托给 [ReplicaScrollUpdater]）
     */
    fun clearAllScrollUpdates() {
        ReplicaScrollUpdater.clearAllScrollUpdates()
    }

    /**
     * 发送复刻通知，与原通知保持一致
     * @return 通知ID，如果发送失败则返回null
     */
    internal suspend fun sendReplicaNotification(
        context: Context,
        key: String,
        title: String?,
        text: String?,
        appName: String?,
        paramV2: ParamV2?,
        paramV2Raw: String?,
        picMap: Map<String, String>?,
        sourceId: String,
        floatingWindowManager: FloatingWindowManager,
        overrideNotificationId: Int? = null,
    ): Int? {
        try {
            // 验证规范信息注入开关状态，确保至少有一种开启
            ReplicaIntentFactory.validateSpecInjectionSwitches(context)

            // 共享通知ID模式下（列表模式），清理旧的滚动任务避免冲突
            if (overrideNotificationId != null) {
                ReplicaScrollUpdater.stopOtherScrollUpdatesExcept(key)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 生成唯一的通知ID（列表模式使用固定ID，以便原地更新）
            val notificationId = overrideNotificationId ?: (key.hashCode().and(0xffff) + NOTIFICATION_BASE_ID)

            // 计算点击/删除意图所需的条件标志位
            val intentFlags = ReplicaIntentFactory.computeIntentFlags(context)
            val needClickIntent = intentFlags.needClickIntent

            // 创建点击意图，用于处理用户点击通知时切换浮窗或切换列表
            val contentIntent =
                ReplicaIntentFactory.createContentIntent(
                    context = context,
                    key = key,
                    title = title,
                    text = text,
                    appName = appName,
                    paramV2Raw = paramV2Raw,
                    picMap = picMap,
                    floatingWindowManager = floatingWindowManager,
                    needClickIntent = needClickIntent,
                    sourceId = sourceId,
                )

            val pendingContentIntent =
                ReplicaIntentFactory.createPendingContentIntent(
                    context = context,
                    notificationId = notificationId,
                    contentIntent = contentIntent,
                    needClickIntent = needClickIntent,
                )

            // 检查是否为媒体类型的超级岛浮窗
            val isMediaType = paramV2?.business == "media"

            // 创建删除意图，用于处理用户移除通知时关闭浮窗
            val deleteIntent =
                ReplicaIntentFactory.createDeleteIntent(
                    context = context,
                    notificationId = notificationId,
                    needClickIntent = needClickIntent,
                )

            // 统一使用"超级岛复刻"通知渠道
            ReplicaIntentFactory.ensureChannel(context, notificationManager)

            if (isMediaType) {
                // 检查规范信息注入模式
                val isLiveUpdatesEnabled = SuperIslandConfigUtils.isLiveUpdatesSpecInjectionEnabled(context)

                val builder =
                    NotificationCompat
                        .Builder(context, NOTIFICATION_CHANNEL_ID)
                        .setContentText(title ?: "未知")
                        .setSmallIcon(android.R.drawable.stat_notify_more) // 使用系统默认图标
                        // 调整为不可被一键清除的属性，只能手动划去
                        .setOngoing(true) // 不允许通知被一键清除
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setShowWhen(false)
                        .setWhen(System.currentTimeMillis())
                        .setOnlyAlertOnce(true)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .apply {
                            // 仅在 Live Updates 模式下设置提升标记；
                            // 超级岛模式设置后 SystemUI 会误判为 LiveUpdates 类并用 custom 结构渲染（左岛空白）
                            if (isLiveUpdatesEnabled) {
                                setRequestPromotedOngoing(true)
                            }
                        }

                // 浮窗或列表模式下设置删除意图和点击意图
                if (needClickIntent) {
                    builder
                        .setDeleteIntent(deleteIntent)
                        .setContentIntent(pendingContentIntent)
                }

                // 构建通知（媒体分支的具体逻辑见 [MediaReplicaNotifier]）
                val notification =
                    MediaReplicaNotifier.build(
                        context = context,
                        builder = builder,
                        title = title,
                        text = text,
                        appName = appName,
                        paramV2Raw = paramV2Raw,
                        picMap = picMap,
                        paramV2 = paramV2,
                        key = key,
                        notificationId = notificationId,
                        notificationManager = notificationManager,
                    )

                // 发送通知
                notificationManager.notify(notificationId, notification)
            } else {
                // 非媒体类型，使用原来的通知渠道和构建方式
                val builder =
                    NotificationCompat
                        .Builder(context, NOTIFICATION_CHANNEL_ID)
                        // 具体标题/内容/smallIcon/chronometer 等由 [GeneralReplicaNotifier] 设置
                        .setOnlyAlertOnce(true)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

                // 浮窗或列表模式下设置删除意图和点击意图
                if (needClickIntent) {
                    builder
                        .setDeleteIntent(deleteIntent)
                        .setContentIntent(pendingContentIntent)
                }

                // 构建通知（非媒体分支的具体逻辑见 [GeneralReplicaNotifier]）
                val notification =
                    GeneralReplicaNotifier.build(
                        context = context,
                        builder = builder,
                        title = title,
                        text = text,
                        appName = appName,
                        paramV2Raw = paramV2Raw,
                        picMap = picMap,
                        paramV2 = paramV2,
                        key = key,
                    )

                // 发送通知
                notificationManager.notify(notificationId, notification)
            }

            // 保存entryKey到notificationId的映射
            FloatingReplicaMappingManager
                .putNotificationId(key, notificationId)

            Logger.i(TAG, "超级岛 发送复刻通知成功，key=$key, notificationId=$notificationId")
            return notificationId
        } catch (e: CancellationException) {
            // 协程取消必须原样抛出，避免被下面的 catch(Exception) 吞掉后继续执行 notify 流程
            throw e
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛 发送复刻通知失败: ${e.message}")
            return null
        }
    }

    /**
     * 取消复刻通知
     */
    internal fun cancelReplicaNotification(
        context: Context,
        key: String,
    ) {
        try {
            val notificationId =
                FloatingReplicaMappingManager
                    .removeNotificationId(key)
            if (notificationId != null) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
                // 停止对应的滚动更新
                stopScrollUpdate(key)
                Logger.i(TAG, "超级岛 取消复刻通知成功，key=$key, notificationId=$notificationId")
            }
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛 取消复刻通知失败: ${e.message}")
        }
    }

    /**
     * 清除所有复刻通知
     */
    internal fun clearAllReplicaNotifications(context: Context?) {
        try {
            if (context != null) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                // 取消所有映射中的通知
                val allIds =
                    FloatingReplicaMappingManager
                        .getAllNotificationIds()
                allIds.forEach { (key, notificationId) ->
                    notificationManager.cancel(notificationId)
                    // 停止对应的滚动更新
                    stopScrollUpdate(key)
                    Logger.i(TAG, "超级岛 取消复刻通知成功，key=$key, notificationId=$notificationId")
                }
            }

            // 清空映射
            FloatingReplicaMappingManager
                .clearAllNotificationIds()
            // 清空所有内容指纹
            FloatingReplicaMappingManager
                .clearAllNotificationFingerprints()
            // 清空所有滚动更新
            clearAllScrollUpdates()
            Logger.i(TAG, "超级岛 清除所有复刻通知成功")
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛 清除所有复刻通知失败: ${e.message}")
        }
    }
}
