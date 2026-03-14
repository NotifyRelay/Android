package com.xzyht.notifyrelay.ui.ViewModels

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xzyht.notifyrelay.feature.notification.superisland.history.SuperIslandHistoryEntry
import com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
import notifyrelay.data.database.dao.SuperIslandPackageCount
import notifyrelay.data.database.entity.SuperIslandHistoryEntity
import notifyrelay.data.database.repository.DatabaseRepository

class SuperIslandPagingSource(
    private val repository: DatabaseRepository,
    private val context: Context
) : PagingSource<Int, GroupedSuperIslandHistory>() {
    private val gson = Gson()
    private val stringStringMapType = object : TypeToken<Map<String, String>>() {}.type
    private var cachedGroups: List<PackageGroup>? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, GroupedSuperIslandHistory> {
        return try {
            val offset = params.key ?: 0
            val groups = cachedGroups ?: run {
                val packageCounts = repository.getSuperIslandPackageCount()
                val groupedPackages = packageCounts.map { count ->
                    PackageGroup(
                        packageName = count.packageName,
                        count = count.count,
                        latestTime = count.latestTime
                    )
                }
                cachedGroups = groupedPackages
                groupedPackages
            }

            if (groups.isEmpty()) {
                return LoadResult.Page(emptyList(), prevKey = null, nextKey = null)
            }

            val pageGroups = groups.drop(offset).take(params.loadSize)
            val data = pageGroups.map { group ->
                val entities = repository.getSuperIslandHistoryByPackage(
                    if (group.packageName == "(未知应用)") null else group.packageName
                )
                val entries = entities.map { it.toSuperIslandHistoryEntry(context) }
                val appName = entries.firstOrNull()?.appName?.takeIf { !it.isNullOrBlank() }
                    ?: group.packageName

                GroupedSuperIslandHistory(
                    packageName = group.packageName,
                    appName = appName,
                    latestTime = group.latestTime,
                    entries = entries
                )
            }

            val nextKey = if (offset + pageGroups.size >= groups.size) null else offset + pageGroups.size
            val prevKey = if (offset == 0) null else maxOf(offset - params.loadSize, 0)

            LoadResult.Page(
                data = data,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, GroupedSuperIslandHistory>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchorPosition) ?: return null
        return page.prevKey?.let { it + state.config.pageSize } ?: page.nextKey?.let { it - state.config.pageSize }
    }

    private suspend fun SuperIslandHistoryEntity.toSuperIslandHistoryEntry(
        context: Context
    ): SuperIslandHistoryEntry {
        val rawMap: Map<String, String> = try {
            gson.fromJson(picMap, stringStringMapType) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
        val resolvedMap = try {
            SuperIslandImageStore.resolvePicMap(context, rawMap)
        } catch (_: Exception) {
            rawMap
        }
        return SuperIslandHistoryEntry(
            id = id,
            sourceDeviceUuid = sourceDeviceUuid,
            originalPackage = originalPackage,
            mappedPackage = mappedPackage,
            appName = appName,
            title = title,
            text = text,
            paramV2Raw = paramV2Raw,
            picMap = resolvedMap,
            rawPayload = rawPayload,
            featureId = featureId
        )
    }

    private data class PackageGroup(
        val packageName: String,
        val count: Int,
        val latestTime: Long
    )
}
