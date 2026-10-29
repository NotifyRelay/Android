package notifyrelay.desktop.platform

import androidx.paging.PagingSource
import com.xzyht.notifyrelay.nativecore.device.appsync.RemoteAppIconCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.data.database.history.SuperIslandHistoryStore
import notifyrelay.data.database.paging.SuperIslandHistoryPagingSource
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.domain.feature.notification.history.AppIconInfo
import notifyrelay.domain.feature.notification.history.GroupedSuperIslandHistory
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryEntry
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryPort

/**
 * 桌面端超级岛历史端口。
 *
 * 分页源、详情读取与清空均复用公共实现；桌面端无本机应用图标来源，
 * 故 [appIcons] 的应用名退化为包名、图标为 null。
 */
class DesktopSuperIslandHistoryPort(
    private val repository: DatabaseRepository,
) : SuperIslandHistoryPort {
    private val historyChangedState = MutableStateFlow(0L)
    override val historyChanged: StateFlow<Long> = historyChangedState

    override val iconUpdates: StateFlow<Pair<String, Long>?> get() = RemoteAppIconCache.iconUpdates

    override fun pagingSource(): PagingSource<Int, GroupedSuperIslandHistory> = SuperIslandHistoryPagingSource(repository)

    override suspend fun appIcons(packageNames: List<String>): Map<String, AppIconInfo> = packageNames.associateWith { packageName -> AppIconInfo(appName = packageName, icon = null) }

    override suspend fun loadEntryDetail(id: Long): SuperIslandHistoryEntry? = SuperIslandHistoryStore.loadEntryDetail(id)?.toDomainEntry()

    override suspend fun deleteEntry(id: Long) {
        repository.deleteSuperIslandHistoryById(id)
        historyChangedState.value = historyChangedState.value + 1
    }

    override suspend fun deleteGroup(packageName: String) {
        repository.deleteSuperIslandHistoryByPackage(if (packageName == UNKNOWN_PACKAGE_LABEL) null else packageName)
        historyChangedState.value = historyChangedState.value + 1
    }

    override suspend fun clearHistory() {
        repository.deleteOldSuperIslandHistory(0)
        historyChangedState.value = historyChangedState.value + 1
    }

    private fun notifyrelay.data.database.history.SuperIslandHistoryStoreEntry.toDomainEntry(): SuperIslandHistoryEntry =
        SuperIslandHistoryEntry(
            id = id,
            sourceDeviceUuid = sourceDeviceUuid,
            originalPackage = originalPackage,
            mappedPackage = mappedPackage,
            appName = appName,
            title = title,
            text = text,
            paramV2Raw = paramV2Raw,
            picMap = picMap,
            rawPayload = rawPayload,
            featureId = featureId,
        )

    private companion object {
        /** 无法归属到具体包名时的分组标签。 */
        const val UNKNOWN_PACKAGE_LABEL = "(未知应用)"
    }
}
