package notifyrelay.desktop.platform

import androidx.compose.ui.graphics.ImageBitmap
import com.xzyht.notifyrelay.nativecore.device.appsync.RemoteAppIconCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.domain.feature.appslist.model.LocalAppInfo
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.shared.feature.appslist.AppIconPort
import notifyrelay.shared.feature.appslist.LocalAppsPort

/**
 * 桌面端远端应用列表端口。
 *
 * 列表来自公共缓存；本机应用不列出，因此 [launchApp] 走 scrcpy 启动层（P3 接线前为空操作）。
 */
class DesktopRemoteAppsPort : notifyrelay.domain.feature.appslist.RemoteAppsPort {
    private val pinned = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    override val iconUpdates: StateFlow<Pair<String, Long>?> get() = RemoteAppIconCache.iconUpdates

    override val pinnedApps: StateFlow<Map<String, Set<String>>> = pinned

    override suspend fun remoteApps(deviceUuid: String): List<notifyrelay.domain.feature.appslist.model.RemoteAppInfo> =
        com.xzyht.notifyrelay.nativecore.device.appsync.RemoteAppsCacheCore
            .list(deviceUuid)

    override fun loadPinned(deviceUuid: String) = Unit

    override fun pin(
        deviceUuid: String,
        packageName: String,
    ) = Unit

    override fun unpin(
        deviceUuid: String,
        packageName: String,
    ) = Unit

    override suspend fun requestAppList(deviceUuid: String): Boolean = false

    override fun findOnlineDevice(deviceUuid: String): DeviceInfo? = null

    override fun launchApp(
        app: notifyrelay.domain.feature.appslist.model.RemoteAppInfo,
        deviceIp: String,
        useScrcpyStartApp: Boolean,
    ) = Unit
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
 * 图标字节来自公共缓存；本机无图标来源（[localIcon] 恒 null）。
 * 字节到 [ImageBitmap] 的解码在 #30 落地后启用，当前返回 null。
 */
class DesktopAppIconPort : AppIconPort {
    override val iconUpdates: StateFlow<Pair<String, Long>?>
        get() = RemoteAppIconCache.iconUpdates

    override suspend fun localIcon(packageName: String): ImageBitmap? = null

    override suspend fun externalIcon(packageName: String): ImageBitmap? = null

    override suspend fun externalIcons(packageNames: List<String>): Map<String, ImageBitmap?> = emptyMap()

    override suspend fun iconWithAutoRequest(
        packageName: String,
        sourceDevice: DeviceInfo?,
    ): ImageBitmap? = null

    override suspend fun cacheExternalIcon(
        packageName: String,
        icon: ImageBitmap?,
        deviceUuid: String,
    ) = Unit
}
