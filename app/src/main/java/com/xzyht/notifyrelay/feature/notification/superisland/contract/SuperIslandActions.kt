package com.xzyht.notifyrelay.feature.notification.superisland.contract

/**
 * 超级岛通知的广播 action 契约。
 *
 * 生产侧（[com.xzyht.notifyrelay.feature.notification.superisland.notification.LiveUpdatesIntentFactory]、
 * [com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils]）与
 * 消费侧（[com.xzyht.notifyrelay.feature.notification.superisland.receiver.NotificationBroadcastReceiver]）
 * 共用此处的常量，避免靠注释维系的隐式契约。
 *
 * **字符串值不可更改**：action 是跨进程 / 跨版本契约，改名会使旧 PendingIntent 失配。
 * 该文件刻意不依赖任何其它类型，以免形成环状依赖。
 */
object SuperIslandActions {
    /** 点击通知切换浮窗/列表。 */
    const val TOGGLE_FLOATING = "com.xzyht.notifyrelay.ACTION_TOGGLE_FLOATING"

    /** 用户移除通知时关闭浮窗/列表条目。 */
    const val CLOSE_NOTIFICATION = "com.xzyht.notifyrelay.ACTION_CLOSE_NOTIFICATION"
}
