package com.xzyht.notifyrelay.feature.notification.history

import notifyrelay.data.database.dao.PackageCount
import notifyrelay.data.database.entity.NotificationRecordEntity
import notifyrelay.data.database.paging.GroupedPagingSource
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.domain.feature.notification.filter.RemoteFilterPortRegistry
import notifyrelay.domain.feature.notification.history.GroupedNotifications
import notifyrelay.domain.feature.notification.history.NotificationHistoryEntry

/**
 * 通知历史分页源：按（映射后的）包名分组，每组内按时间倒序。
 *
 * 分页骨架与超级岛历史共用 [GroupedPagingSource]；本类只负责把远端包名映射成本机等价包名后分组。
 */
class NotificationHistoryPagingSource(
    private val repository: DatabaseRepository,
    private val deviceUuid: String,
    private val installedPackages: Set<String>,
) : GroupedPagingSource<GroupedNotifications>() {
    private var cachedGroups: List<GroupedPackage>? = null

    override suspend fun loadGroupCount(): Int {
        val packageCounts = repository.getPackageCountByDevice(deviceUuid)
        cachedGroups = buildGroupedPackages(packageCounts)
        return cachedGroups?.size ?: 0
    }

    override suspend fun loadPage(
        offset: Int,
        limit: Int,
    ): List<GroupedNotifications> {
        val groups = cachedGroups ?: emptyList()
        return groups.drop(offset).take(limit).map { group ->
            val records =
                group.packageNames
                    .flatMap { packageName -> repository.getNotificationsByPackageAndDevice(packageName, deviceUuid) }
                    .sortedByDescending { it.time }
            val notifications = records.map { it.toNotificationEntry() }
            val appName = notifications.firstOrNull()?.appName?.takeIf { it.isNotBlank() } ?: group.groupKey
            GroupedNotifications(
                packageName = group.groupKey,
                appName = appName,
                latestTime = group.latestTime,
                notifications = notifications,
            )
        }
    }

    override fun onLoadError(cause: Exception): LoadResult.Error<Int, GroupedNotifications> = LoadResult.Error(cause)

    private fun buildGroupedPackages(packageCounts: List<PackageCount>): List<GroupedPackage> {
        val grouped = linkedMapOf<String, GroupAccumulator>()

        for (count in packageCounts) {
            val mappedPackage = RemoteFilterPortRegistry.current?.mapToLocalPackage(count.packageName, installedPackages) ?: count.packageName
            val group = grouped.getOrPut(mappedPackage) { GroupAccumulator(mappedPackage) }
            group.packageNames.add(count.packageName)
            group.latestTime = maxOf(group.latestTime, count.latestTime)
        }

        return grouped.values
            .map { accumulator ->
                GroupedPackage(
                    groupKey = accumulator.groupKey,
                    packageNames = accumulator.packageNames.toList(),
                    latestTime = accumulator.latestTime,
                )
            }.sortedByDescending { it.latestTime }
    }

    private fun NotificationRecordEntity.toNotificationEntry(): NotificationHistoryEntry =
        NotificationHistoryEntry(
            key = key,
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            time = time,
            device = deviceUuid,
        )

    private data class GroupedPackage(
        val groupKey: String,
        val packageNames: List<String>,
        val latestTime: Long,
    )

    private class GroupAccumulator(
        val groupKey: String,
        val packageNames: LinkedHashSet<String> = linkedSetOf(),
        var latestTime: Long = 0L,
    )
}
