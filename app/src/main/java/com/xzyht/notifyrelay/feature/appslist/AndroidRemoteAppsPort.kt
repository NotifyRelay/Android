package com.xzyht.notifyrelay.feature.appslist

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.domain.feature.appslist.RemoteAppsPort
import notifyrelay.domain.feature.appslist.model.RemoteAppInfo
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.shared.feature.appslist.PinnedAppsPortRegistry

/** 以现有仓库与服务实现 [RemoteAppsPort]。 */
class AndroidRemoteAppsPort(
    private val context: Context,
) : RemoteAppsPort {
    override val iconUpdates: StateFlow<Pair<String, Long>?> get() = AppIconRepository.iconUpdates

    override val pinnedApps: StateFlow<Map<String, Set<String>>>
        get() = PinnedAppsPortRegistry.current?.pinnedApps ?: PinnedAppsRepository.pinnedApps

    override suspend fun remoteApps(deviceUuid: String): List<RemoteAppInfo> = RemoteAppsCache.getRemoteAppsList(context, deviceUuid)

    override fun loadPinned(deviceUuid: String) {
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

    override suspend fun requestAppList(deviceUuid: String): Boolean {
        val deviceManager =
            com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
                .getDeviceMessage(context)
        val deviceInfo = findOnlineDevice(deviceUuid) ?: return false
        com.xzyht.notifyrelay.feature.appslist.sync.AppListSyncManager.requestAppListFromDevice(
            context,
            deviceManager,
            deviceInfo,
        )
        return true
    }

    override fun findOnlineDevice(deviceUuid: String): DeviceInfo? {
        val deviceManager =
            com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
                .getDeviceMessage(context)
        return deviceManager.getAuthenticatedOnlineDevices().find { it.uuid == deviceUuid }
    }

    override fun launchApp(
        app: RemoteAppInfo,
        deviceIp: String,
        useScrcpyStartApp: Boolean,
    ) {
        io.github.miuzarte.scrcpyforandroid.pages.ShortcutLaunchActivity.startFullscreenControl(
            context = context,
            ip = deviceIp,
            port = 5555,
            name = app.appName,
            startApp = app.packageName,
            useScrcpyStartApp = useScrcpyStartApp,
        )
    }
}
