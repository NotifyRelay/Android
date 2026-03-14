package com.xzyht.notifyrelay.ui.ViewModels

import com.xzyht.notifyrelay.feature.notification.superisland.history.SuperIslandHistoryEntry

data class SuperIslandHistoryUiState(
    val expandedGroups: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class GroupedSuperIslandHistory(
    val packageName: String,
    val appName: String?,
    val latestTime: Long,
    val entries: List<SuperIslandHistoryEntry>,
    val isExpanded: Boolean = false
)
