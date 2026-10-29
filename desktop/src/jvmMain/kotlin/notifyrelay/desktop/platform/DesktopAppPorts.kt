package notifyrelay.desktop.platform

import androidx.compose.ui.graphics.ImageBitmap
import com.xzyht.notifyrelay.nativecore.device.appsync.AppListSyncManagerCore
import com.xzyht.notifyrelay.nativecore.device.appsync.IconSyncManagerCore
import com.xzyht.notifyrelay.nativecore.device.appsync.RemoteAppIconCache
import com.xzyht.notifyrelay.nativecore.device.appsync.RemoteAppsCacheCore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.data.repository.applist.PinnedAppsRepository
import notifyrelay.domain.feature.appslist.RemoteAppsPort
import notifyrelay.domain.feature.appslist.model.LocalAppInfo
import notifyrelay.domain.feature.appslist.model.RemoteAppInfo
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.device.service.contract.DeviceMessagePort
import notifyrelay.shared.feature.appslist.AppIconPort
import notifyrelay.shared.feature.appslist.LocalAppsPort

/**
 * 桌面端远端应用列表端口。
 *
 * 列表来自公共缓存；请求经公共同步管理器发出，与 Android 同一条报文链路。
 * 本机应用不列出；[launchApp] 经 scrcpy 以 `--start-app` 在远端拉起应用。
 *
 * @param deviceMessagePort 提供当前设备消息端口的访问器（设备管理器在数据层之后创建）
 */
class DesktopRemoteAppsPort(
    private val deviceMessagePort: () -> DeviceMessagePort?,
) : RemoteAppsPort {
    override val iconUpdates: StateFlow<Pair<String, Long>?> get() = RemoteAppIconCache.iconUpdates

    override val pinnedApps: StateFlow<Map<String, Set<String>>> get() = PinnedAppsRepository.pinnedApps

    override suspend fun remoteApps(deviceUuid: String): List<RemoteAppInfo> = RemoteAppsCacheCore.list(deviceUuid)

    override fun loadPinned(deviceUuid: String) = PinnedAppsRepository.loadPinnedApps(deviceUuid)

    override fun pin(
        deviceUuid: String,
        packageName: String,
    ) = PinnedAppsRepository.pinApp(deviceUuid, packageName)

    override fun unpin(
        deviceUuid: String,
        packageName: String,
    ) = PinnedAppsRepository.unpinApp(deviceUuid, packageName)

    override suspend fun requestAppList(deviceUuid: String): Boolean {
        val manager = deviceMessagePort() ?: return false
        val deviceInfo = findOnlineDevice(deviceUuid) ?: return false
        AppListSyncManagerCore.requestAppList(manager, deviceInfo)
        return true
    }

    override fun findOnlineDevice(deviceUuid: String): DeviceInfo? {
        val manager = deviceMessagePort() ?: return null
        return manager.getAuthenticatedOnlineDevices().find { it.uuid == deviceUuid }
    }

    override fun launchApp(
        app: RemoteAppInfo,
        deviceUuid: String,
        useScrcpyStartApp: Boolean,
    ) {
        if (!useScrcpyStartApp) return
        // 应用列表携带的设备 UUID 直接对应 adb 设备，无需经 IP 反查
        val device = findOnlineDevice(deviceUuid)
        if (device == null) {
            DesktopToastHost.show("未找到该在线设备，无法启动应用", DesktopToastHost.Duration.LONG)
            return
        }
        val started =
            DesktopScrcpyLauncher.launch(
                deviceUuid = device.uuid,
                deviceName = device.displayName,
                deviceIp = device.ip,
                startApp = app.packageName,
                startAppTitle = app.appName,
            )
        if (started) {
            DesktopToastHost.show("正在启动「" + app.appName + "」", DesktopToastHost.Duration.SHORT)
        }
    }
}

/** 桌面端本机应用端口：无已安装应用概念，返回空列表。 */
class DesktopLocalAppsPort : LocalAppsPort {
    private val isLoadingState = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = isLoadingState

    private val appsState = MutableStateFlow<List<LocalAppInfo>>(emptyList())
    override val apps: StateFlow<List<LocalAppInfo>> = appsState

    override suspend fun load() = Unit

    override fun filtered(
        query: String,
        showSystemApps: Boolean,
    ): List<LocalAppInfo> = emptyList()

    override suspend fun iconFor(packageName: String): ImageBitmap? = null

    override fun isDataLoaded(): Boolean = true

    override fun installedPackageNames(): Set<String> = emptySet()

    override fun installedPackageNamesSync(): Set<String> = emptySet()
}

/**
 * 桌面端应用图标端口。
 *
 * 图标字节来自公共缓存并经 Skia 解码；本机无图标来源（[localIcon] 恒 null）。
 * 缺失图标时按来源设备向对端发起请求，与 Android 同一条报文链路。
 */
class DesktopAppIconPort(
    private val deviceMessagePort: () -> DeviceMessagePort?,
) : AppIconPort {
    override val iconUpdates: StateFlow<Pair<String, Long>?> get() = RemoteAppIconCache.iconUpdates

    override suspend fun localIcon(packageName: String): ImageBitmap? = null

    override suspend fun externalIcon(packageName: String): ImageBitmap? = RemoteAppIconCache.cachedBytes(packageName)?.let { DesktopIconCodecPort.decodeToBitmap(it) }

    override suspend fun externalIcons(packageNames: List<String>): Map<String, ImageBitmap?> =
        RemoteAppIconCache
            .cachedBytes(packageNames)
            .mapValues { (_, bytes) -> bytes?.let { DesktopIconCodecPort.decodeToBitmap(it) } }

    override suspend fun iconWithAutoRequest(
        packageName: String,
        sourceDevice: DeviceInfo?,
    ): ImageBitmap? {
        externalIcon(packageName)?.let { return it }
        val manager = deviceMessagePort()
        if (manager != null && sourceDevice != null) {
            IconSyncManagerCore.checkAndSyncIcon(packageName, manager, sourceDevice)
        }
        return null
    }

    override suspend fun cacheExternalIcon(
        packageName: String,
        icon: ImageBitmap?,
        deviceUuid: String,
    ) = Unit
}
