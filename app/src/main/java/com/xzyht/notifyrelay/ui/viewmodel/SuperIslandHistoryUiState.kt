package com.xzyht.notifyrelay.ui.viewmodel

import com.xzyht.notifyrelay.feature.notification.superisland.history.SuperIslandHistoryStoreEntry

data class SuperIslandHistoryUiState(
    val expandedGroups: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class GroupedSuperIslandHistory(
    val packageName: String,
    val appName: String?,
    val latestTime: Long,
    val entries: List<SuperIslandHistoryStoreEntry>,
    val isExpanded: Boolean = false,
)
