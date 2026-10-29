package com.xzyht.notifyrelay.feature.appslist

import kotlinx.coroutines.flow.StateFlow
import notifyrelay.data.repository.applist.PinnedAppsRepository
import notifyrelay.shared.feature.appslist.PinnedAppsPort

/**
 * 以 [PinnedAppsRepository] 实现 [PinnedAppsPort]。
 */
class AndroidPinnedAppsPort : PinnedAppsPort {
    override val pinnedApps: StateFlow<Map<String, Set<String>>> get() = PinnedAppsRepository.pinnedApps

    override fun load(deviceUuid: String) {
        PinnedAppsRepository.loadPinnedApps(deviceUuid)
    }

    override fun pin(
        deviceUuid: String,
        packageName: String,
    ) {
        PinnedAppsRepository.pinApp(deviceUuid, packageName)
    }

    override fun unpin(
        deviceUuid: String,
        packageName: String,
    ) {
        PinnedAppsRepository.unpinApp(deviceUuid, packageName)
    }

    override fun isPinned(
        deviceUuid: String,
        packageName: String,
    ): Boolean = PinnedAppsRepository.isAppPinned(deviceUuid, packageName)
}
