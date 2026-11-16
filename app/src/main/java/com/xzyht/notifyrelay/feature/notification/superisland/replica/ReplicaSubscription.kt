package com.xzyht.notifyrelay.feature.notification.superisland.replica

/**
 * 点击通知切换展示所需的订阅信息。
 *
 * 门面与浮窗管理器两处都以同一组入参接收它：参数列表在两侧各自展开会随实现漂移，
 * 收紧到本类型后，新增一项只改一处。
 */
class ReplicaSubscription(
    val sourceId: String,
    val title: String?,
    val text: String?,
    val paramV2Raw: String?,
    val picMap: Map<String, String>?,
    val appName: String?,
)
