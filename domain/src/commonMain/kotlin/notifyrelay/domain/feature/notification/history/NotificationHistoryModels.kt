package notifyrelay.domain.feature.notification.history

/** 通知历史条目（平台无关视图）。 */
data class NotificationHistoryEntry(
    val key: String,
    val packageName: String,
    val appName: String?,
    val title: String?,
    val text: String?,
    val time: Long,
    val device: String,
)

/** 按应用分组的通知历史。 */
data class GroupedNotifications(
    val packageName: String,
    val appName: String,
    val latestTime: Long,
    val notifications: List<NotificationHistoryEntry>,
    val isExpanded: Boolean = false,
)

/** 通知历史 UI 状态。 */
data class NotificationHistoryUiState(
    val selectedDevice: String = "本机",
    val deviceList: List<String> = listOf("本机"),
    val expandedGroups: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** 超级岛历史条目（平台无关视图）。 */
data class SuperIslandHistoryEntry(
    val id: Long,
    val sourceDeviceUuid: String? = null,
    val originalPackage: String? = null,
    val mappedPackage: String? = null,
    val appName: String? = null,
    val title: String? = null,
    val text: String? = null,
    val paramV2Raw: String? = null,
    val picMap: Map<String, String> = emptyMap(),
    val rawPayload: String? = null,
    val featureId: String? = null,
)

/** 按应用分组的超级岛历史。 */
data class GroupedSuperIslandHistory(
    val packageName: String,
    val appName: String?,
    val latestTime: Long,
    val entries: List<SuperIslandHistoryEntry>,
    val isExpanded: Boolean = false,
)

/** 超级岛历史 UI 状态。 */
data class SuperIslandHistoryUiState(
    val expandedGroups: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
