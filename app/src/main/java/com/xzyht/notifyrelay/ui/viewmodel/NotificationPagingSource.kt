package com.xzyht.notifyrelay.ui.viewmodel

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.xzyht.notifyrelay.feature.notification.backend.RemoteFilterConfig
import com.xzyht.notifyrelay.sync.notification.data.NotificationRecord
import notifyrelay.data.database.dao.PackageCount
import notifyrelay.data.database.entity.NotificationRecordEntity
import notifyrelay.data.database.repository.DatabaseRepository
import kotlin.math.max

class NotificationPagingSource(
    private val repository: DatabaseRepository,
    private val deviceUuid: String,
    private val installedPackages: Set<String>
) : PagingSource<Int, GroupedNotifications>() {
    private var cachedGroups: List<GroupedPackage>? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, GroupedNotifications> {
        return try {
            val offset = params.key ?: 0
            val groups = cachedGroups ?: run {
                val packageCounts = repository.getPackageCountByDevice(deviceUuid)
                val groupedPackages = buildGroupedPackages(packageCounts)
                cachedGroups = groupedPackages
                groupedPackages
            }

            if (groups.isEmpty()) {
                return LoadResult.Page(emptyList(), prevKey = null, nextKey = null)
            }

            val pageGroups = groups.drop(offset).take(params.loadSize)
            val data = pageGroups.map { group ->
                val records = group.packageNames.flatMap { packageName ->
                    repository.getNotificationsByPackageAndDevice(packageName, deviceUuid)
                }.sortedByDescending { it.time }

                val notifications = records.map { it.toNotificationRecord() }
                val appName = notifications.firstOrNull()?.appName?.takeIf { it.isNotBlank() } ?: group.groupKey

                GroupedNotifications(
                    packageName = group.groupKey,
                    appName = appName,
                    latestTime = group.latestTime,
                    notifications = notifications
                )
            }

            val nextKey = if (offset + pageGroups.size >= groups.size) null else offset + pageGroups.size
            val prevKey = if (offset == 0) null else max(offset - params.loadSize, 0)

            LoadResult.Page(
                data = data,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, GroupedNotifications>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchorPosition) ?: return null
        return page.prevKey?.let { it + state.config.pageSize } ?: page.nextKey?.let { it - state.config.pageSize }
    }

    private fun buildGroupedPackages(packageCounts: List<PackageCount>): List<GroupedPackage> {
        val grouped = linkedMapOf<String, GroupAccumulator>()

        for (count in packageCounts) {
            val mappedPackage = RemoteFilterConfig.mapToLocalPackage(count.packageName, installedPackages)
            val group = grouped.getOrPut(mappedPackage) {
                GroupAccumulator(mappedPackage)
            }
            group.packageNames.add(count.packageName)
            group.latestTime = max(group.latestTime, count.latestTime)
        }

        return grouped.values
            .map { accumulator ->
                GroupedPackage(
                    groupKey = accumulator.groupKey,
                    packageNames = accumulator.packageNames.toList(),
                    latestTime = accumulator.latestTime
                )
            }
            .sortedByDescending { it.latestTime }
    }

    private fun NotificationRecordEntity.toNotificationRecord(): NotificationRecord {
        return NotificationRecord(
            key = key,
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            time = time,
            device = deviceUuid
        )
    }

    private data class GroupedPackage(
        val groupKey: String,
        val packageNames: List<String>,
        val latestTime: Long
    )

    private class GroupAccumulator(
        val groupKey: String,
        val packageNames: LinkedHashSet<String> = linkedSetOf(),
        var latestTime: Long = 0L
    )
}
