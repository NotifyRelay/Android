package com.xzyht.notifyrelay.feature.notification.superisland.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.contract.SuperIslandActions
import com.xzyht.notifyrelay.feature.notification.superisland.replica.FloatingReplicaManager
import notifyrelay.base.util.Logger

/**
 * 通知广播接收器，用于处理超级岛通知的点击和关闭事件
 *
 * action 契约由 [SuperIslandActions] 统一承载（生产侧 LiveUpdatesIntentFactory /
 * SuperIslandConfigUtils 引用同一常量），不再靠注释维系。
 *
 * 耦合逻辑说明：
 * 1. 浮窗功能与通知点击事件的耦合：
 *    - 接收 [SuperIslandActions.TOGGLE_FLOATING] 广播，处理通知点击事件
 *    - 接收 [SuperIslandActions.CLOSE_NOTIFICATION] 广播，处理通知关闭事件
 *    - 当浮窗功能开启时，调用 FloatingReplicaManager 的相应方法处理浮窗状态
 *
 * 2. 通知与浮窗的去耦合：
 *    - 通过 SUPER_ISLAND_FLOATING_WINDOW_KEY 开关控制浮窗功能
 *    - 浮窗功能关闭时，不处理与浮窗相关的广播
 *    - 浮窗功能关闭时，通知点击和关闭事件不会触发浮窗操作
 */
class NotificationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        val floatingWindowEnabled = SuperIslandConfigUtils.isFloatingWindowEnabled(context)
        // needClickIntent 的三判统一到配置类（D3）：此处与生产侧共用同一判定
        val needClickIntent = SuperIslandConfigUtils.needClickIntent(context)

        when (action) {
            SuperIslandActions.CLOSE_NOTIFICATION -> {
                if (!needClickIntent) {
                    Logger.i("超级岛", "浮窗/列表均未开启，不处理关闭通知广播")
                    return
                }

                val notificationId = intent.getIntExtra("notificationId", 0)
                Logger.i("超级岛", "接收到关闭通知广播，notificationId=$notificationId")
                FloatingReplicaManager.closeByNotificationId(notificationId)
            }
            SuperIslandActions.TOGGLE_FLOATING -> {
                if (!needClickIntent) {
                    Logger.i("超级岛", "浮窗/列表均未开启，不处理切换广播")
                    return
                }

                val sourceId = intent.getStringExtra("sourceId") ?: return
                val title = intent.getStringExtra("title")
                val text = intent.getStringExtra("text")
                val appName = intent.getStringExtra("appName")
                val paramV2Raw = intent.getStringExtra("paramV2Raw")

                val picMapBundle = intent.getBundleExtra("picMap")
                val picMap = mutableMapOf<String, String>()
                picMapBundle?.keySet()?.forEach { key ->
                    picMapBundle.getString(key)?.let { value ->
                        picMap[key] = value
                    }
                }

                // 保持原日志文案：浮窗与列表的各自状态分开打印
                Logger.i("超级岛", "接收到切换广播，浮窗=$floatingWindowEnabled, 列表=${SuperIslandConfigUtils.isNotificationListMode(context)}, sourceId=$sourceId")
                FloatingReplicaManager.toggleFloating(
                    context,
                    sourceId,
                    title,
                    text,
                    paramV2Raw,
                    if (picMap.isEmpty()) null else picMap,
                    appName,
                )
            }
            else -> {
                Logger.w("超级岛", "接收到未知广播动作: $action")
            }
        }
    }
}
