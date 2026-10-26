package com.xzyht.notifyrelay.feature.notification.history

import android.content.Context
import androidx.compose.ui.graphics.asImageBitmap
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.domain.feature.notification.history.AppIconInfo
import notifyrelay.domain.feature.notification.history.GroupedNotifications
import notifyrelay.domain.feature.notification.history.GroupedSuperIslandHistory
import notifyrelay.domain.feature.notification.history.NotificationHistoryPort
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryEntry
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryPort

/** 以数据库与现有仓库实现通知历史端口。 */
class AndroidNotificationHistoryPort(
    private val context: Context,
) : NotificationHistoryPort {
    private val _historyChanged = MutableStateFlow(0L)
    override val historyChanged: StateFlow<Long> = _historyChanged

    override val iconUpdates: StateFlow<Pair<String, Long>?>
        get() = com.xzyht.notifyrelay.feature.appslist.AppIconRepository.iconUpdates

    private var currentDevice: String = "本机"

    override fun pagingSource(
        deviceUuid: String,
        installedPackages: Set<String>,
    ): PagingSource<Int, GroupedNotifications> =
        NotificationHistoryPagingSource(
            repository = DatabaseRepository.getInstance(),
            deviceUuid = deviceUuid,
            installedPackages = installedPackages,
        )

    override fun deviceList(): List<String> = com.xzyht.notifyrelay.feature.device.model.NotificationRepository.deviceList

    override fun selectDevice(device: String) {
        currentDevice = device
        com.xzyht.notifyrelay.feature.device.model.NotificationRepository.currentDevice = device
        _historyChanged.value = _historyChanged.value + 1
    }

    override fun installedPackages(): Set<String> =
        com.xzyht.notifyrelay.feature.appslist.InstalledAppsRepository
            .getInstalledPackageNames(context)

    override suspend fun appIcons(packageNames: List<String>): Map<String, AppIconInfo> =
        packageNames.associateWith { pkg ->
            val appName =
                runCatching {
                    notifyrelay.base.util.AppListHelper
                        .getApplicationLabel(context, pkg)
                }.getOrDefault(pkg)
            val icon =
                runCatching {
                    com.xzyht.notifyrelay.feature.appslist.AppIconRepository
                        .getAppIconAsync(context, pkg)
                        ?.asImageBitmap()
                }.getOrNull()
            AppIconInfo(appName = appName, icon = icon)
        }

    override suspend fun deleteNotification(key: String) {
        DatabaseRepository.getInstance().deleteNotificationByKey(key)
        _historyChanged.value = _historyChanged.value + 1
    }

    override suspend fun deleteGroup(packageName: String) {
        DatabaseRepository
            .getInstance()
            .deleteNotificationsByPackageAndDevice(packageName, currentDevice)
        _historyChanged.value = _historyChanged.value + 1
    }

    override suspend fun clearHistory() {
        DatabaseRepository.getInstance().deleteNotificationsByDevice(currentDevice)
        _historyChanged.value = _historyChanged.value + 1
    }
}

/** 以数据库实现超级岛历史端口。 */
class AndroidSuperIslandHistoryPort(
    private val context: Context,
) : SuperIslandHistoryPort {
    private val _historyChanged = MutableStateFlow(0L)
    override val historyChanged: StateFlow<Long> = _historyChanged

    override val iconUpdates: StateFlow<Pair<String, Long>?>
        get() = com.xzyht.notifyrelay.feature.appslist.AppIconRepository.iconUpdates

    override fun pagingSource(): PagingSource<Int, GroupedSuperIslandHistory> =
        SuperIslandHistoryPagingSource(
            repository = DatabaseRepository.getInstance(),
            context = context,
        )

    override suspend fun appIcons(packageNames: List<String>): Map<String, AppIconInfo> =
        packageNames.associateWith { pkg ->
            val appName =
                runCatching {
                    notifyrelay.base.util.AppListHelper
                        .getApplicationLabel(context, pkg)
                }.getOrDefault(pkg)
            val icon =
                runCatching {
                    com.xzyht.notifyrelay.feature.appslist.AppIconRepository
                        .getAppIconAsync(context, pkg)
                        ?.asImageBitmap()
                }.getOrNull()
            AppIconInfo(appName = appName, icon = icon)
        }

    override suspend fun loadEntryDetail(id: Long): SuperIslandHistoryEntry? =
        com.xzyht.notifyrelay.feature.notification.superisland.history.SuperIslandHistoryStore
            .loadEntryDetail(context, id)
            ?.toDomainEntry()

    override suspend fun deleteEntry(id: Long) {
        DatabaseRepository.getInstance().deleteSuperIslandHistoryById(id)
        _historyChanged.value = _historyChanged.value + 1
    }

    override suspend fun deleteGroup(packageName: String) {
        DatabaseRepository
            .getInstance()
            .deleteSuperIslandHistoryByPackage(if (packageName == "(未知应用)") null else packageName)
        _historyChanged.value = _historyChanged.value + 1
    }

    override suspend fun clearHistory() {
        DatabaseRepository.getInstance().deleteOldSuperIslandHistory(0)
        _historyChanged.value = _historyChanged.value + 1
    }

    private fun com.xzyht.notifyrelay.feature.notification.superisland.history.SuperIslandHistoryStoreEntry.toDomainEntry(): SuperIslandHistoryEntry =
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
}
