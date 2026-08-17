package com.xzyht.notifyrelay.feature.appslist.model

data class RemoteAppInfo(
    val packageName: String,
    val appName: String,
    val iconBytes: ByteArray? = null,
    val isPinned: Boolean = false,
    var isLoading: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RemoteAppInfo
        if (packageName != other.packageName) return false
        if (appName != other.appName) return false
        if (isPinned != other.isPinned) return false
        return true
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + appName.hashCode()
        result = 31 * result + isPinned.hashCode()
        return result
    }
}

data class RemoteAppsState(
    val apps: List<RemoteAppInfo> = emptyList(),
    val pinnedApps: List<RemoteAppInfo> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val error: String? = null,
) {
    val filteredApps: List<RemoteAppInfo>
        get() {
            if (searchQuery.isBlank()) return apps
            return apps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }

    val hasApps: Boolean
        get() = apps.isNotEmpty() || pinnedApps.isNotEmpty()

    val isEmpty: Boolean
        get() = !isLoading && !hasApps && error == null
}
