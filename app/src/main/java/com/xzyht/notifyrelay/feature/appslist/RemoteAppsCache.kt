package com.xzyht.notifyrelay.feature.appslist

import android.content.Context
import com.xzyht.notifyrelay.feature.appslist.model.RemoteAppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import notifyrelay.data.database.entity.AppDeviceEntity
import notifyrelay.data.database.entity.AppEntity

/**
 * 远程应用缓存。
 *
 * 负责远端设备应用列表的内存缓存与数据库持久化，以及按设备维度读取远程应用明细：
 * - [cacheRemoteAppList] 缓存远端上报的应用列表（Map<包名, 应用名>）到内存与数据库
 * - [getRemoteAppsList] 按设备 UUID 从数据库读取远程应用并附带置顶状态
 *
 * [AppRepository] 作为门面转发本 object 的公开方法，保持既有调用方不变。
 */
internal object RemoteAppsCache {
    private val _remoteApps = MutableStateFlow<Map<String, String>>(emptyMap())
    val remoteApps: StateFlow<Map<String, String>> = _remoteApps.asStateFlow()

    /**
     * 清空远程应用列表的内存缓存（供 [InstalledAppsRepository.clearCache] 调用）。
     */
    fun clearRemoteApps() {
        _remoteApps.value = emptyMap()
    }

    /**
     * 缓存远程应用列表。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @param apps 远程应用列表，格式为 Map<包名, 应用名>
     * @param deviceUuid 远程设备UUID
     */
    suspend fun cacheRemoteAppList(
        context: Context,
        apps: Map<String, String>,
        deviceUuid: String,
    ) {
        AppDatabaseHolder.init(context)

        val appEntities = mutableListOf<AppEntity>()
        val appDeviceEntities = mutableListOf<AppDeviceEntity>()

        apps.forEach { (packageName, appName) ->
            // 检查应用是否已存在
            val existingApp = AppDatabaseHolder.get()?.getAppByPackageName(packageName)
            val appEntity =
                if (existingApp != null) {
                    // 更新现有应用
                    existingApp.copy(
                        appName = appName,
                        lastUpdated = System.currentTimeMillis(),
                    )
                } else {
                    // 创建新应用
                    AppEntity(
                        packageName = packageName,
                        appName = appName,
                        isSystemApp = false,
                        iconBytes = null,
                        isIconMissing = true,
                        lastUpdated = System.currentTimeMillis(),
                    )
                }
            appEntities.add(appEntity)

            // 创建应用设备关联
            val appDeviceEntity =
                AppDeviceEntity(
                    packageName = packageName,
                    sourceDevice = deviceUuid,
                    lastUpdated = System.currentTimeMillis(),
                )
            appDeviceEntities.add(appDeviceEntity)
        }

        // 保存到数据库
        if (appEntities.isNotEmpty()) {
            AppDatabaseHolder.get()?.saveApps(appEntities)
        }
        if (appDeviceEntities.isNotEmpty()) {
            AppDatabaseHolder.get()?.saveAppDeviceAssociations(appDeviceEntities)
        }

        _remoteApps.value = apps
        // Logger.d(TAG, "缓存远程应用列表成功，共 ${apps.size} 个应用")
    }

    /**
     * 获取指定设备的远程应用列表（含置顶状态）。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @param deviceUuid 远程设备UUID。
     * @return 按「置顶优先、其次应用名」排序的远程应用列表。
     */
    suspend fun getRemoteAppsList(
        context: Context,
        deviceUuid: String,
    ): List<RemoteAppInfo> {
        AppDatabaseHolder.init(context)
        val appDevices = AppDatabaseHolder.get()?.getAppDevicesByDeviceUuid(deviceUuid)?.first() ?: emptyList()
        val packageNames = appDevices.map { it.packageName }.distinct()
        if (packageNames.isEmpty()) return emptyList()
        val apps = AppDatabaseHolder.get()?.getAppsByPackageNames(packageNames) ?: emptyList()
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
