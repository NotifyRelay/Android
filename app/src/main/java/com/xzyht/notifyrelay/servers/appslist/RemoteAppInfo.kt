package com.xzyht.notifyrelay.servers.appslist

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
        return packageName == other.packageName
    }

    override fun hashCode(): Int {
        return packageName.hashCode()
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
