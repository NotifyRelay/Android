package notifyrelay.desktop.platform

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.domain.feature.notification.history.AppIconInfo
import notifyrelay.domain.feature.notification.history.GroupedNotifications
import notifyrelay.domain.feature.notification.history.NotificationHistoryPort

/**
 * 桌面端通知历史端口。
 *
 * 直接从 Room 读取通知记录并按应用分组；应用图标与已安装应用列表在桌面端暂不可得，
 * 因此图标为空、已安装包集合为空（包名映射退化为原包名）。
 */
class DesktopHistoryPort(
    private val repository: DatabaseRepository,
) : NotificationHistoryPort {
    private val _historyChanged = MutableStateFlow(0L)
    override val historyChanged: StateFlow<Long> = _historyChanged

    private val _iconUpdates = MutableStateFlow<Pair<String, Long>?>(null)
    override val iconUpdates: StateFlow<Pair<String, Long>?> = _iconUpdates

    private var currentDevice: String = "本机"

    override fun pagingSource(
        deviceUuid: String,
        installedPackages: Set<String>,
    ): PagingSource<Int, GroupedNotifications> = DesktopNotificationPagingSource(repository, deviceUuid)

    override fun deviceList(): List<String> = listOf("本机")

    override fun selectDevice(device: String) {
        currentDevice = device
        _historyChanged.value = _historyChanged.value + 1
    }

    override fun installedPackages(): Set<String> = emptySet()

    override suspend fun appIcons(packageNames: List<String>): Map<String, AppIconInfo> = packageNames.associateWith { pkg -> AppIconInfo(appName = pkg, icon = null) }

    override suspend fun deleteNotification(key: String) {
        repository.deleteNotificationByKey(key)
        _historyChanged.value = _historyChanged.value + 1
    }

    override suspend fun deleteGroup(packageName: String) {
        repository.deleteNotificationsByPackageAndDevice(packageName, currentDevice)
        _historyChanged.value = _historyChanged.value + 1
    }

    override suspend fun clearHistory() {
        repository.deleteNotificationsByDevice(currentDevice)
        _historyChanged.value = _historyChanged.value + 1
    }
}

/**
 * 桌面端通知历史分页源：按应用分组，每组内按时间倒序。
 *
 * 与 Android 侧相比不做包名等价映射（桌面端无已安装应用概念）。
 */
private class DesktopNotificationPagingSource(
    private val repository: DatabaseRepository,
    private val deviceUuid: String,
) : PagingSource<Int, GroupedNotifications>() {
    private var cachedGroups: List<PackageGroup>? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, GroupedNotifications> =
        try {
            val offset = params.key ?: 0
            val groups =
                cachedGroups ?: run {
                    val counts = repository.getPackageCountByDevice(deviceUuid)
                    counts
                        .map { PackageGroup(it.packageName, it.latestTime) }
                        .sortedByDescending { it.latestTime }
                        .also { cachedGroups = it }
                }

            if (groups.isEmpty()) return LoadResult.Page(emptyList(), prevKey = null, nextKey = null)

            val pageGroups = groups.drop(offset).take(params.loadSize)
            val data =
                pageGroups.map { group ->
                    val records =
                        repository
                            .getNotificationsByPackageAndDevice(group.packageName, deviceUuid)
                            .sortedByDescending { it.time }
                    val entries =
                        records.map {
                            notifyrelay.domain.feature.notification.history.NotificationHistoryEntry(
                                key = it.key,
                                packageName = it.packageName,
                                appName = it.appName,
                                title = it.title,
                                text = it.text,
                                time = it.time,
                                device = it.deviceUuid,
                            )
                        }
                    GroupedNotifications(
                        packageName = group.packageName,
                        appName = entries.firstOrNull()?.appName?.takeIf { it.isNotBlank() } ?: group.packageName,
                        latestTime = group.latestTime,
                        notifications = entries,
                    )
                }

            val nextKey = if (offset + pageGroups.size >= groups.size) null else offset + pageGroups.size
            val prevKey = if (offset == 0) null else maxOf(offset - params.loadSize, 0)
            LoadResult.Page(data = data, prevKey = prevKey, nextKey = nextKey)
        } catch (e: Exception) {
            LoadResult.Error(e)
        }

    override fun getRefreshKey(state: PagingState<Int, GroupedNotifications>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(state.config.pageSize) ?: page.nextKey?.minus(state.config.pageSize)
    }

    private data class PackageGroup(
        val packageName: String,
        val latestTime: Long,
    )
}
