package com.xzyht.notifyrelay.feature.notification.history

import androidx.compose.ui.graphics.asImageBitmap
import androidx.paging.PagingSource
import com.xzyht.notifyrelay.feature.appslist.AppIconRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.base.util.AppListHelper
import notifyrelay.data.database.history.SuperIslandHistoryStore
import notifyrelay.data.database.paging.SuperIslandHistoryPagingSource
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.domain.feature.notification.history.AppIconInfo
import notifyrelay.domain.feature.notification.history.GroupedSuperIslandHistory
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryEntry
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryPort

/**
 * 超级岛历史端口（Android）。
 *
 * 分页源与历史存储位于公共端；本类只补平台专属的应用名与图标来源。
 */
class AndroidSuperIslandHistoryPort(
    private val context: android.content.Context,
) : SuperIslandHistoryPort {
    private val historyChangedState = MutableStateFlow(0L)
    override val historyChanged: StateFlow<Long> = historyChangedState

    override val iconUpdates: StateFlow<Pair<String, Long>?>
        get() = AppIconRepository.iconUpdates

    override fun pagingSource(): PagingSource<Int, GroupedSuperIslandHistory> = SuperIslandHistoryPagingSource(DatabaseRepository.getInstance())

    override suspend fun appIcons(packageNames: List<String>): Map<String, AppIconInfo> =
        packageNames.associateWith { packageName ->
            val appName =
                runCatching {
                    AppListHelper.getApplicationLabel(context, packageName)
                }.getOrDefault(packageName)
            val icon =
                runCatching {
                    AppIconRepository.getAppIconAsync(context, packageName)?.asImageBitmap()
                }.getOrNull()
            AppIconInfo(appName = appName, icon = icon)
        }

    override suspend fun loadEntryDetail(id: Long): SuperIslandHistoryEntry? = SuperIslandHistoryStore.loadEntryDetail(id)?.toDomainEntry()

    override suspend fun deleteEntry(id: Long) {
        DatabaseRepository.getInstance().deleteSuperIslandHistoryById(id)
        historyChangedState.value = historyChangedState.value + 1
    }

    override suspend fun deleteGroup(packageName: String) {
        DatabaseRepository
            .getInstance()
            .deleteSuperIslandHistoryByPackage(if (packageName == UNKNOWN_PACKAGE_LABEL) null else packageName)
        historyChangedState.value = historyChangedState.value + 1
    }

    override suspend fun clearHistory() {
        DatabaseRepository.getInstance().deleteOldSuperIslandHistory(0)
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
