package com.xzyht.notifyrelay.nativecore.device.appsync

import kotlinx.coroutines.flow.first
import notifyrelay.data.database.entity.AppDeviceEntity
import notifyrelay.data.database.entity.AppEntity
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.data.repository.applist.PinnedAppsRepository
import notifyrelay.domain.feature.appslist.model.RemoteAppInfo

/**
 * 远端应用列表缓存。
 *
 * 负责远端设备应用列表的数据库持久化，以及按设备维度读取远程应用明细：
 * - [cache] 缓存远端上报的应用列表（包名 → 应用名）
 * - [list] 按设备 UUID 读取远程应用并附带置顶状态
 */
object RemoteAppsCacheCore {
    /** 缓存远程应用列表。 */
    suspend fun cache(
        apps: Map<String, String>,
        deviceUuid: String,
    ) {
        val repo = DatabaseRepository.getInstance()
        val now = System.currentTimeMillis()
        val appEntities = mutableListOf<AppEntity>()
        val appDeviceEntities = mutableListOf<AppDeviceEntity>()

        apps.forEach { (packageName, appName) ->
            val existingApp = repo.getAppByPackageName(packageName)
            val appEntity =
                if (existingApp != null) {
                    existingApp.copy(appName = appName, lastUpdated = now)
                } else {
                    AppEntity(
                        packageName = packageName,
                        appName = appName,
                        isSystemApp = false,
                        iconBytes = null,
                        isIconMissing = true,
                        lastUpdated = now,
                    )
                }
            appEntities.add(appEntity)
            appDeviceEntities.add(
                AppDeviceEntity(
                    packageName = packageName,
                    sourceDevice = deviceUuid,
                    lastUpdated = now,
                ),
            )
        }

        if (appEntities.isNotEmpty()) repo.saveApps(appEntities)
        if (appDeviceEntities.isNotEmpty()) repo.saveAppDeviceAssociations(appDeviceEntities)
    }

    /** 获取指定设备的远程应用列表（置顶优先，其次按应用名）。 */
    suspend fun list(deviceUuid: String): List<RemoteAppInfo> {
        val repo = DatabaseRepository.getInstance()
        val appDevices = repo.getAppDevicesByDeviceUuid(deviceUuid).first()
        val packageNames = appDevices.map { it.packageName }.distinct()
        if (packageNames.isEmpty()) return emptyList()
        val apps = repo.getAppsByPackageNames(packageNames)
        return apps
            .map { entity ->
                RemoteAppInfo(
                    packageName = entity.packageName,
                    appName = entity.appName,
                    iconBytes = entity.iconBytes,
                    isPinned = PinnedAppsRepository.isAppPinned(deviceUuid, entity.packageName),
                    isLoading = false,
                )
            }.sortedWith(compareByDescending<RemoteAppInfo> { it.isPinned }.thenBy { it.appName })
    }
}
