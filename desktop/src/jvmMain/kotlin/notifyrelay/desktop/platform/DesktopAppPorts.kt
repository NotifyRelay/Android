package notifyrelay.desktop.platform

import androidx.compose.ui.graphics.ImageBitmap
import com.xzyht.notifyrelay.nativecore.device.appsync.AppListSyncManagerCore
import com.xzyht.notifyrelay.nativecore.device.appsync.IconSyncManagerCore
import com.xzyht.notifyrelay.nativecore.device.appsync.RemoteAppIconCache
import com.xzyht.notifyrelay.nativecore.device.appsync.RemoteAppsCacheCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import notifyrelay.base.log.Logger
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.data.repository.applist.PinnedAppsRepository
import notifyrelay.domain.feature.appslist.RemoteAppsPort
import notifyrelay.domain.feature.appslist.model.LocalAppInfo
import notifyrelay.domain.feature.appslist.model.RemoteAppInfo
import notifyrelay.domain.feature.appslist.model.appMatchesQuery
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

/**
 * 桌面端本机应用端口。
 *
 * 桌面端不枚举进程外的本机安装应用，列表取自**历史出现过的本机应用**：
 * 通知采集每次把「应用名 + 包名 + 图标」写入应用表（`apps` 表，来源为
 * `AppInfo.DisplayInfo.GetLogo`），因此条目与图标在通知被清除后仍然保留。
 *
 * 图标与弹幕层同源，均经 [RemoteAppIconCache] 读取；列表仅用于展示与过滤，
 * 桌面端仍不支持启动本机应用。
 */
class DesktopLocalAppsPort : LocalAppsPort {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isLoadingState = MutableStateFlow(true)
    override val isLoading: StateFlow<Boolean> = isLoadingState

    private val appsState = MutableStateFlow<List<LocalAppInfo>>(emptyList())
    override val apps: StateFlow<List<LocalAppInfo>> = appsState

    @Volatile
    private var loaded = false

    @Volatile
    private var loadJob: Job? = null

    init {
        // 装配即加载一次：列表页、过滤器页与图标入口不保证都会主动调用 load()
        loadJob =
            scope.launch {
                load()
            }
    }

    /** 读取历史出现过的本机应用；重复调用无副作用。 */
    override suspend fun load() {
        val list =
            runCatching {
                val repo = DatabaseRepository.getInstance()
                // 「历史出现过」= 关联表登记的来源设备应用 ∪ 现有通知历史中的包名
                val linked = repo.getAppDeviceLinks(LOCAL_DEVICE).map { it.packageName }
                val historical = repo.getNotificationsByDevice(LOCAL_DEVICE).map { it.packageName }
                val packages = (linked + historical).filter { it.isNotBlank() }.distinct()
                repo
                    .getAppsByPackageNames(packages)
                    .filter { it.packageName.isNotBlank() }
                    .map { entity ->
                        LocalAppInfo(
                            packageName = entity.packageName,
                            appName = entity.appName.takeIf { it.isNotBlank() } ?: entity.packageName,
                        )
                    }
            }.onFailure { Logger.w(TAG, "读取本机应用列表失败", it) }
                .getOrDefault(emptyList())
        appsState.value = list
        loaded = true
        isLoadingState.value = false
    }

    override fun filtered(
        query: String,
        showSystemApps: Boolean,
    ): List<LocalAppInfo> {
        val scoped = if (showSystemApps) appsState.value else appsState.value.filterNot { it.isSystemApp }
        if (query.isBlank()) return scoped
        return scoped.filter { appMatchesQuery(it.appName, it.packageName, query) }
    }

    /** 图标取应用图标缓存（含通知采集写入的图标）；本机仍不枚举、不启动应用。 */
    override suspend fun iconFor(packageName: String): ImageBitmap? = RemoteAppIconCache.cachedBytes(packageName)?.let { DesktopIconCodecPort.decodeToBitmap(it) }

    override fun isDataLoaded(): Boolean = loaded

    override fun installedPackageNames(): Set<String> = appsState.value.map { it.packageName }.toSet()

    override fun installedPackageNamesSync(): Set<String> = installedPackageNames()

    private companion object {
        const val TAG = "DesktopLocalApps"

        /** 本机通知在历史表中的设备标识。 */
        const val LOCAL_DEVICE = "本机"
    }
}

/**
 * 桌面端应用图标端口。
 *
 * 图标字节来自公共缓存（含通知采集层的应用图标）并经 Skia 解码；本机不枚举、不启动应用，但图标照常显示。
 * 缺失图标时按来源设备向对端发起请求，与 Android 同一条报文链路。
 */
class DesktopAppIconPort(
    private val deviceMessagePort: () -> DeviceMessagePort?,
) : AppIconPort {
    override val iconUpdates: StateFlow<Pair<String, Long>?> get() = RemoteAppIconCache.iconUpdates

    /** 本机应用图标：取应用图标缓存（含通知采集写入的图标），与本机是否可启动应用无关。 */
    override suspend fun localIcon(packageName: String): ImageBitmap? = RemoteAppIconCache.cachedBytes(packageName)?.let { DesktopIconCodecPort.decodeToBitmap(it) }

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
