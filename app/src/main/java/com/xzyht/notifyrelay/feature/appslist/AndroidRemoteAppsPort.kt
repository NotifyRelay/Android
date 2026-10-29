package com.xzyht.notifyrelay.feature.appslist

import android.content.Context
import com.xzyht.notifyrelay.nativecore.device.appsync.AppListSyncManagerCore
import com.xzyht.notifyrelay.nativecore.device.appsync.RemoteAppsCacheCore
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.data.repository.applist.PinnedAppsRepository
import notifyrelay.domain.feature.appslist.RemoteAppsPort
import notifyrelay.domain.feature.appslist.model.RemoteAppInfo
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.shared.feature.appslist.PinnedAppsPortRegistry

/** 以公共缓存与同步管理器实现 [RemoteAppsPort]。 */
class AndroidRemoteAppsPort(
    private val context: Context,
) : RemoteAppsPort {
    override val iconUpdates: StateFlow<Pair<String, Long>?> get() = AppIconRepository.iconUpdates

    override val pinnedApps: StateFlow<Map<String, Set<String>>>
        get() = PinnedAppsPortRegistry.current?.pinnedApps ?: PinnedAppsRepository.pinnedApps

    override suspend fun remoteApps(deviceUuid: String): List<RemoteAppInfo> = RemoteAppsCacheCore.list(deviceUuid)

    override fun loadPinned(deviceUuid: String) {
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

    override suspend fun requestAppList(deviceUuid: String): Boolean {
        val deviceManager =
            com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
                .getDeviceMessage(context)
        val deviceInfo = findOnlineDevice(deviceUuid) ?: return false
        AppListSyncManagerCore.requestAppList(deviceManager, deviceInfo)
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
