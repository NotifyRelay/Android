package com.xzyht.notifyrelay.feature.notification.superisland

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import notifyrelay.base.util.Logger
import notifyrelay.data.StorageManager

/**
 * 通知广播接收器，用于处理超级岛通知的点击和关闭事件
 * 
 * 耦合逻辑说明：
 * 1. 浮窗功能与通知点击事件的耦合：
 *    - 接收 com.xzyht.notifyrelay.ACTION_TOGGLE_FLOATING 广播，处理通知点击事件
 *    - 接收 com.xzyht.notifyrelay.ACTION_CLOSE_NOTIFICATION 广播，处理通知关闭事件
 *    - 当浮窗功能开启时，调用 FloatingReplicaManager 的相应方法处理浮窗状态
 * 
 * 2. 通知与浮窗的去耦合：
 *    - 通过 SUPER_ISLAND_FLOATING_WINDOW_KEY 开关控制浮窗功能
 *    - 浮窗功能关闭时，不处理与浮窗相关的广播
 *    - 浮窗功能关闭时，通知点击和关闭事件不会触发浮窗操作
 */
class NotificationBroadcastReceiver : BroadcastReceiver() {
    // 浮窗功能开关键
    private val SUPER_ISLAND_FLOATING_WINDOW_KEY = "super_island_floating_window"
    
    /**
     * 检查浮窗功能是否开启
     */
    private fun isFloatingWindowEnabled(context: Context): Boolean {
        return StorageManager.getBoolean(context, SUPER_ISLAND_FLOATING_WINDOW_KEY, true)
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        // 检查浮窗功能是否开启
        val floatingWindowEnabled = isFloatingWindowEnabled(context)
        
        when (action) {
            "com.xzyht.notifyrelay.ACTION_CLOSE_NOTIFICATION" -> {
                // 只有在浮窗功能开启时才处理关闭通知广播
                if (!floatingWindowEnabled) {
                    Logger.i("超级岛", "浮窗功能已关闭，不处理关闭通知广播")
                    return
                }
                
                val notificationId = intent.getIntExtra("notificationId", 0)
                Logger.i("超级岛", "接收到关闭通知广播，notificationId=$notificationId")
                // 调用FloatingReplicaManager的方法关闭对应的浮窗
                FloatingReplicaManager.closeByNotificationId(notificationId)
            }
            "com.xzyht.notifyrelay.ACTION_TOGGLE_FLOATING" -> {
                // 只有在浮窗功能开启时才处理切换浮窗广播
                if (!floatingWindowEnabled) {
                    Logger.i("超级岛", "浮窗功能已关闭，不处理切换浮窗广播")
                    return
                }
                
                // 提取重建浮窗所需的数据
                val sourceId = intent.getStringExtra("sourceId") ?: return
                val title = intent.getStringExtra("title")
                val text = intent.getStringExtra("text")
                val appName = intent.getStringExtra("appName")
                val paramV2Raw = intent.getStringExtra("paramV2Raw")
                
                // 提取图片映射
                val picMapBundle = intent.getBundleExtra("picMap")
                val picMap = mutableMapOf<String, String>()
                picMapBundle?.keySet()?.forEach { key ->
                    picMapBundle.getString(key)?.let { value ->
                        picMap[key] = value
                    }
                }
                
                Logger.i("超级岛", "接收到切换浮窗广播，sourceId=$sourceId")
                // 调用FloatingReplicaManager的方法切换浮窗状态
                FloatingReplicaManager.toggleFloating(
                    context, sourceId, title, text, paramV2Raw, 
                    if (picMap.isEmpty()) null else picMap, 
                    appName
                )
            }
            else -> {
                Logger.w("超级岛", "接收到未知广播动作: $action")
            }
        }
    }
}
