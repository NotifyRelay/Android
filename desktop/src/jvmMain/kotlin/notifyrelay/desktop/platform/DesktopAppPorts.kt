package notifyrelay.desktop.platform

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.domain.feature.appslist.model.LocalAppInfo
import notifyrelay.domain.feature.notification.filter.RemoteFilterPort
import notifyrelay.shared.feature.appslist.AppIconPort
import notifyrelay.shared.feature.appslist.LocalAppsPort

/**
 * 桌面端远端过滤端口：默认不过滤。
 *
 * 桌面端暂无远端通知源，过滤规则保持中性；接入真实数据后改为持久化配置。
 */
class DesktopRemoteFilterPort : RemoteFilterPort {
    override var filterMode: String = "none"
    override var enableDedup: Boolean = false
    override var enablePackageGroupMapping: Boolean = false
    override var enableLockScreenOnly: Boolean = false
    override val defaultPackageGroups: List<List<String>> = emptyList()
    override var defaultGroupEnabled: List<Boolean> = emptyList()
    override var customPackageGroups: List<List<String>> = emptyList()
    override var customGroupEnabled: List<Boolean> = emptyList()
    override var isLoaded: Boolean = true

    override suspend fun load() = Unit

    override suspend fun save() = Unit

    override fun activeFilterList(): List<Pair<String, String?>> = emptyList()

    override fun isActiveEntryEnabled(
        packageName: String,
        keyword: String,
    ): Boolean = false

    override suspend fun setEntryEnabled(
        packageName: String,
        keyword: String,
        enabled: Boolean,
    ) = Unit

    override suspend fun addEntry(
        packageName: String,
        keyword: String,
    ) = Unit

    override suspend fun removeEntry(
        packageName: String,
        keyword: String,
    ) = Unit

    override fun mapToLocalPackage(
        packageName: String,
        installedPackages: Set<String>,
    ): String = packageName
}

/** 桌面端本机应用端口：无已安装应用概念，返回空列表。 */
class DesktopLocalAppsPort : LocalAppsPort {
    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading

    private val _apps = MutableStateFlow<List<LocalAppInfo>>(emptyList())
    override val apps: StateFlow<List<LocalAppInfo>> = _apps

    override suspend fun load() = Unit

    override fun filtered(
        query: String,
        includeSystem: Boolean,
    ): List<LocalAppInfo> = emptyList()

    override suspend fun iconFor(packageName: String): ImageBitmap? = null

    override fun isDataLoaded(): Boolean = true

    override fun installedPackageNames(): Set<String> = emptySet()

    override fun installedPackageNamesSync(): Set<String> = emptySet()
}

/** 桌面端应用图标端口：暂无图标来源。 */
class DesktopAppIconPort : AppIconPort {
    private val _iconUpdates = MutableStateFlow<Pair<String, Long>?>(null)
    override val iconUpdates: StateFlow<Pair<String, Long>?> = _iconUpdates

    override suspend fun localIcon(packageName: String): ImageBitmap? = null

    override suspend fun externalIcon(packageName: String): ImageBitmap? = null

    override suspend fun externalIcons(packageNames: List<String>): Map<String, ImageBitmap?> = emptyMap()

    override suspend fun iconWithAutoRequest(
        packageName: String,
        sourceDevice: notifyrelay.domain.feature.device.model.DeviceInfo?,
    ): ImageBitmap? = null

    override fun defaultIcon(size: Int): ImageBitmap? = null

    override suspend fun cacheExternalIcon(
        packageName: String,
        icon: ImageBitmap?,
        deviceUuid: String,
    ) = Unit
}
