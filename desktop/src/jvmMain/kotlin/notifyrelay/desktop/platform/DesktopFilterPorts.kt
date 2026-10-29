package notifyrelay.desktop.platform

import kotlinx.coroutines.flow.StateFlow
import notifyrelay.data.filter.LocalFilterRepository
import notifyrelay.data.filter.RemoteFilterRepository
import notifyrelay.data.repository.applist.PinnedAppsRepository
import notifyrelay.domain.feature.notification.filter.FilterEntry
import notifyrelay.domain.feature.notification.filter.LocalFilterPort
import notifyrelay.domain.feature.notification.filter.RemoteFilterPort
import notifyrelay.shared.feature.appslist.PinnedAppsPort

/**
 * 桌面端远端过滤端口。
 *
 * 与 Android 共用 [RemoteFilterRepository]；本机已安装包集合恒为空集，
 * 因此包名映射退化为原包名。
 */
class DesktopRemoteFilterPort(
    private val repository: RemoteFilterRepository,
) : RemoteFilterPort {
    override var filterMode: String
        get() = repository.filterMode
        set(value) {
            repository.filterMode = value
        }

    override var enableDedup: Boolean
        get() = repository.enableDedup
        set(value) {
            repository.enableDedup = value
        }

    override var enablePackageGroupMapping: Boolean
        get() = repository.enablePackageGroupMapping
        set(value) {
            repository.enablePackageGroupMapping = value
        }

    override var enableLockScreenOnly: Boolean
        get() = repository.enableLockScreenOnly
        set(value) {
            repository.enableLockScreenOnly = value
        }

    override var enablePeerMode: Boolean
        get() = repository.enablePeerMode
        set(value) {
            repository.enablePeerMode = value
        }

    override val defaultPackageGroups: List<List<String>>
        get() = repository.defaultPackageGroups

    override var defaultGroupEnabled: List<Boolean>
        get() = repository.defaultGroupEnabled
        set(value) {
            repository.defaultGroupEnabled = value
        }

    override var customPackageGroups: List<List<String>>
        get() = repository.customPackageGroups
        set(value) {
            repository.customPackageGroups = value
        }

    override var customGroupEnabled: List<Boolean>
        get() = repository.customGroupEnabled
        set(value) {
            repository.customGroupEnabled = value
        }

    override var isLoaded: Boolean
        get() = repository.isLoaded
        set(value) {
            repository.isLoaded = value
        }

    override suspend fun load() = repository.load()

    override suspend fun save() = repository.save()

    override fun activeFilterList(): List<Pair<String, String?>> = repository.activeFilterList()

    override fun isActiveEntryEnabled(
        packageName: String,
        keyword: String,
    ): Boolean = repository.isActiveEntryEnabled(packageName, keyword)

    override suspend fun setEntryEnabled(
        packageName: String,
        keyword: String,
        enabled: Boolean,
    ) = repository.setEntryEnabled(packageName, keyword, enabled)

    override suspend fun addEntry(
        packageName: String,
        keyword: String,
    ) = repository.addEntry(packageName, keyword)

    override suspend fun removeEntry(
        packageName: String,
        keyword: String,
    ) = repository.removeEntry(packageName, keyword)

    override fun mapToLocalPackage(
        packageName: String,
        installedPackages: Set<String>,
    ): String = repository.mapToLocalPackage(packageName, installedPackages)

    override fun checkFilterAccepted(
        packageName: String,
        title: String,
        text: String,
    ): Boolean = repository.checkFilterAccepted(packageName, title, text)

    /** 已启用组构成的包名等价组。 */
    fun packageGroups(): List<Set<String>> = repository.packageGroups
}

/** 桌面端本地过滤端口：与 Android 共用条目仓储。 */
class DesktopLocalFilterPort : LocalFilterPort {
    override var filterSelf: Boolean
        get() = LocalFilterRepository.filterSelf
        set(value) {
            LocalFilterRepository.filterSelf = value
        }

    override var filterOngoing: Boolean
        get() = LocalFilterRepository.filterOngoing
        set(value) {
            LocalFilterRepository.filterOngoing = value
        }

    override var filterNoTitleOrText: Boolean
        get() = LocalFilterRepository.filterNoTitleOrText
        set(value) {
            LocalFilterRepository.filterNoTitleOrText = value
        }

    override var filterImportanceNone: Boolean
        get() = LocalFilterRepository.filterImportanceNone
        set(value) {
            LocalFilterRepository.filterImportanceNone = value
        }

    override fun builtinKeywords(): Set<String> = LocalFilterRepository.getBuiltinKeywords()

    override fun defaultPackageFilters(): Set<String> = LocalFilterRepository.getDefaultPackageFilters()

    override suspend fun entries(): Set<FilterEntry> = LocalFilterRepository.getFilterEntries()

    override suspend fun enabledEntries(): Set<FilterEntry> = LocalFilterRepository.getEnabledFilterEntries()

    override suspend fun setEntryEnabled(
        entry: FilterEntry,
        enabled: Boolean,
    ) = LocalFilterRepository.setFilterEntryEnabled(entry, enabled)

    override suspend fun addEntry(
        keyword: String,
        packageName: String,
    ) = LocalFilterRepository.addFilterEntry(keyword, packageName)

    override suspend fun removeEntry(
        keyword: String,
        packageName: String,
    ) = LocalFilterRepository.removeFilterEntry(keyword, packageName)
}

/** 桌面端置顶应用端口：与 Android 共用公共仓库。 */
class DesktopPinnedAppsPort : PinnedAppsPort {
    override val pinnedApps: StateFlow<Map<String, Set<String>>> get() = PinnedAppsRepository.pinnedApps

    override fun load(deviceUuid: String) = PinnedAppsRepository.loadPinnedApps(deviceUuid)

    override fun pin(
        deviceUuid: String,
        packageName: String,
    ) = PinnedAppsRepository.pinApp(deviceUuid, packageName)

    override fun unpin(
        deviceUuid: String,
        packageName: String,
    ) = PinnedAppsRepository.unpinApp(deviceUuid, packageName)

    override fun isPinned(
        deviceUuid: String,
        packageName: String,
    ): Boolean = PinnedAppsRepository.isAppPinned(deviceUuid, packageName)
}
