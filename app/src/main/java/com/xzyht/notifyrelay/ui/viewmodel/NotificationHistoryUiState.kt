package com.xzyht.notifyrelay.ui.viewmodel

import com.xzyht.notifyrelay.sync.notification.data.NotificationRecord

/**
 * 通知历史 UI 状态
 */
data class NotificationHistoryUiState(
    val selectedDevice: String = "本机",
    val deviceList: List<String> = listOf("本机"),
    val expandedGroups: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * 分组通知数据
 */
data class GroupedNotifications(
    val packageName: String,
    val appName: String,
    val latestTime: Long,
    val notifications: List<NotificationRecord>,
    val isExpanded: Boolean = false
)
