package notifyrelay.data.database.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import notifyrelay.data.database.entity.SuperIslandHistoryEntity
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.domain.feature.notification.history.GroupedSuperIslandHistory
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryEntry

/**
 * 超级岛历史分页源：按包名分组，每组内按时间倒序。
 *
 * 分组统计与组内条目均来自数据库；`picMap` 中的数字图片 id 会解析为图片原文。
 */
class SuperIslandHistoryPagingSource(
    private val repository: DatabaseRepository,
) : PagingSource<Int, GroupedSuperIslandHistory>() {
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedGroups: List<PackageGroup>? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, GroupedSuperIslandHistory> {
        return try {
            val offset = params.key ?: 0
            val groups =
                cachedGroups ?: run {
                    val packageCounts = repository.getSuperIslandPackageCount()
                    val groupedPackages =
                        packageCounts.map { count ->
                            PackageGroup(
                                packageName = count.packageName,
                                count = count.count,
                                latestTime = count.latestTime,
                            )
                        }
                    cachedGroups = groupedPackages
                    groupedPackages
                }

            if (groups.isEmpty()) {
                return LoadResult.Page(emptyList(), prevKey = null, nextKey = null)
            }

            val pageGroups = groups.drop(offset).take(params.loadSize)
            val data =
                pageGroups.map { group ->
                    val entities =
                        repository.getSuperIslandHistoryByPackage(
                            if (group.packageName == UNKNOWN_PACKAGE_LABEL) null else group.packageName,
                        )
                    val entries = entities.map { it.toDomainEntry() }
                    val appName =
                        entries.firstOrNull()?.appName?.takeIf { it.isNotBlank() }
                            ?: group.packageName

                    GroupedSuperIslandHistory(
                        packageName = group.packageName,
                        appName = appName,
                        latestTime = group.latestTime,
                        entries = entries,
                    )
                }

            val nextKey = if (offset + pageGroups.size >= groups.size) null else offset + pageGroups.size
            val prevKey = if (offset == 0) null else maxOf(offset - params.loadSize, 0)

            LoadResult.Page(
                data = data,
                prevKey = prevKey,
                nextKey = nextKey,
            )
        } catch (e: IllegalStateException) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, GroupedSuperIslandHistory>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchorPosition) ?: return null
        return page.prevKey?.let { it + state.config.pageSize } ?: page.nextKey?.let { it - state.config.pageSize }
    }

    private suspend fun SuperIslandHistoryEntity.toDomainEntry(): SuperIslandHistoryEntry {
        val resolvedMap = resolvePicMap(parsePicMap(picMap))
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
            featureId = featureId,
        )
    }

    /** 解析 `picMap` 的 JSON 字符串；非法 JSON 退化为空表。 */
    private fun parsePicMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = json.parseToJsonElement(raw) as? JsonObject ?: return emptyMap()
            obj.entries.associate { (key, value) -> key to value.jsonPrimitive.content }
        } catch (e: IllegalArgumentException) {
            emptyMap()
        }
    }

    /** 把数字图片 id 解析为图片原文；非数字或反查失败时保留原值。 */
    private suspend fun resolvePicMap(picMap: Map<String, String>): Map<String, String> {
        if (picMap.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, String>()
        for ((key, value) in picMap) {
            if (value.isBlank()) continue
            val imageId = value.toLongOrNull()
            if (imageId == null || imageId <= 0) {
                out[key] = value
            } else {
                val data = repository.resolveSuperIslandImageById(imageId)
                out[key] = if (!data.isNullOrBlank()) data else value
            }
        }
        return out
    }

    private data class PackageGroup(
        val packageName: String,
        val count: Int,
        val latestTime: Long,
    )

    private companion object {
        /** 无法归属到具体包名时的分组标签。 */
        const val UNKNOWN_PACKAGE_LABEL = "(未知应用)"
    }
}
