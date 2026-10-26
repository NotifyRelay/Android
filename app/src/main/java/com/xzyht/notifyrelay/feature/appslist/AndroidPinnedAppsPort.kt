package com.xzyht.notifyrelay.feature.appslist

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.shared.feature.appslist.PinnedAppsPort

/**
 * 以 [PinnedAppsRepository] 实现 [PinnedAppsPort]。
 */
class AndroidPinnedAppsPort(
    private val context: Context,
) : PinnedAppsPort {
    override val pinnedApps: StateFlow<Map<String, Set<String>>> get() = PinnedAppsRepository.pinnedApps

    override fun load(deviceUuid: String) {
        PinnedAppsRepository.loadPinnedApps(context, deviceUuid)
    }

    override fun pin(
        deviceUuid: String,
        packageName: String,
    ) {
        PinnedAppsRepository.pinApp(context, deviceUuid, packageName)
    }

    override fun unpin(
        deviceUuid: String,
        packageName: String,
    ) {
        PinnedAppsRepository.unpinApp(context, deviceUuid, packageName)
    }

    override fun isPinned(
        deviceUuid: String,
        packageName: String,
    ): Boolean = PinnedAppsRepository.isAppPinned(deviceUuid, packageName)
}
