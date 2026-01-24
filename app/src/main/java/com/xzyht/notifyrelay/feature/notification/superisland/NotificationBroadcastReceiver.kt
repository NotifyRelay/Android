package com.xzyht.notifyrelay.feature.notification.superisland

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xzyht.notifyrelay.common.core.util.Logger

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
            else -> {
                Logger.w("超级岛", "接收到未知广播动作: $action")
            }
        }
    }
}
