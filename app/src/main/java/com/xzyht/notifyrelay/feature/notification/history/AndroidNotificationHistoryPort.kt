package com.xzyht.notifyrelay.feature.notification.history

import android.content.Context
import androidx.compose.ui.graphics.asImageBitmap
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.domain.feature.notification.history.AppIconInfo
import notifyrelay.domain.feature.notification.history.GroupedNotifications
import notifyrelay.domain.feature.notification.history.NotificationHistoryPort

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
