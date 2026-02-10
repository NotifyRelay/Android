package com.xzyht.notifyrelay.feature.notification.superisland

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import notifyrelay.core.util.Logger

class NotificationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        when (action) {
            "com.xzyht.notifyrelay.ACTION_CLOSE_NOTIFICATION" -> {
                val notificationId = intent.getIntExtra("notificationId", 0)
                Logger.i("超级岛", "接收到关闭通知广播，notificationId=$notificationId")
                // 调用FloatingReplicaManager的方法关闭对应的浮窗
                FloatingReplicaManager.closeByNotificationId(notificationId)
            }
            "com.xzyht.notifyrelay.ACTION_TOGGLE_FLOATING" -> {
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
