package notifyrelay.data.database.paging

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import notifyrelay.data.database.entity.SuperIslandHistoryEntity
import notifyrelay.data.database.history.SuperIslandHistoryStore
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
) : GroupedPagingSource<GroupedSuperIslandHistory>() {
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedGroups: List<PackageGroup>? = null

    override suspend fun loadGroupCount(): Int {
        val packageCounts = repository.getSuperIslandPackageCount()
        cachedGroups =
            packageCounts.map { count ->
                PackageGroup(packageName = count.packageName, count = count.count, latestTime = count.latestTime)
            }
        return packageCounts.size
    }

    override suspend fun loadPage(
        offset: Int,
        limit: Int,
    ): List<GroupedSuperIslandHistory> {
        val groups = cachedGroups ?: emptyList()
        return groups.drop(offset).take(limit).map { group ->
            val entities =
                repository.getSuperIslandHistoryByPackage(
                    if (group.packageName == UNKNOWN_PACKAGE_LABEL) null else group.packageName,
                )
            val entries = entities.map { it.toDomainEntry() }
            val appName = entries.firstOrNull()?.appName?.takeIf { it.isNotBlank() } ?: group.packageName
            GroupedSuperIslandHistory(
                packageName = group.packageName,
                appName = appName,
                latestTime = group.latestTime,
                entries = entries,
            )
        }
    }

    override fun onLoadError(cause: Exception): LoadResult.Error<Int, GroupedSuperIslandHistory> {
        // 数据库不可用是唯一可恢复的失败；其余异常继续按原样暴露。
        if (cause !is IllegalStateException) throw cause
        return LoadResult.Error(cause)
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

    /** 把数字图片 id 解析为图片原文；实现与历史详情共用。 */
    private suspend fun resolvePicMap(picMap: Map<String, String>): Map<String, String> = SuperIslandHistoryStore.resolvePicMap(picMap) { imageId -> repository.resolveSuperIslandImageById(imageId) }

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
