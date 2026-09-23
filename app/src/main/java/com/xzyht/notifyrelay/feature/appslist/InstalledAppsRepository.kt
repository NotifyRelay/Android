package com.xzyht.notifyrelay.feature.appslist

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import notifyrelay.base.util.Logger
import notifyrelay.base.util.image.toBitmapOrDefault
import notifyrelay.data.database.entity.AppDeviceEntity
import notifyrelay.data.database.entity.AppEntity
import java.io.ByteArrayOutputStream

/**
 * 已安装应用仓库。
 *
 * 负责本机已安装应用的加载、过滤、查询与数据库同步：
 * - [loadApps] 读取 PackageManager 并重建数据库中的应用表
 * - [getFilteredApps] 按关键字与系统应用开关过滤
 * - 包名集合的同步/异步查询
 *
 * [AppRepository] 作为门面转发本 object 的公开方法，保持既有调用方不变。
 */
internal object InstalledAppsRepository {
    private const val TAG = "InstalledAppsRepository"

    // 状态流
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _apps = MutableStateFlow<List<ApplicationInfo>>(emptyList())
    val apps: StateFlow<List<ApplicationInfo>> = _apps.asStateFlow()

    /**
     * 加载应用列表并缓存。
     *
     * 说明：该方法为挂起函数，会从 PackageManager 读取已安装应用信息并按应用标签排序，
     *       同时加载应用图标并保存到数据库。
     *
     * @param context Android 上下文，用于访问 PackageManager 和数据库（非空）。
     * @return 无（在成功或失败后会更新内部状态流 `_apps` 与 `_isLoading`）。
     * @throws Exception 当 PackageManager 访问或数据库操作发生严重错误时向上抛出（调用方可选择捕获）。
     */
    suspend fun loadApps(context: Context) {
        AppDatabaseHolder.init(context)

        _isLoading.value = true
        try {
            // Logger.d(TAG, "开始加载应用列表")
            val apps =
                AppListHelper.getInstalledApplications(context).sortedBy { appInfo ->
                    try {
                        context.packageManager.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        Logger.w(TAG, "获取应用标签失败，使用包名: ${appInfo.packageName}", e)
                        appInfo.packageName
                    }
                }

            _apps.value = apps

            // 保存应用信息到数据库并加载图标
            val appEntities = mutableListOf<AppEntity>()
            val appDeviceEntities = mutableListOf<AppDeviceEntity>()
            val pm = context.packageManager

            apps.forEach { appInfo ->
                try {
                    val packageName = appInfo.packageName
                    val appName =
                        try {
                            pm.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            packageName
                        }
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    // 获取应用图标
                    var iconBytes: ByteArray? = null
                    try {
                        val bitmap =
                            when (val drawable = pm.getApplicationIcon(appInfo)) {
                                is BitmapDrawable -> drawable.bitmap
                                else -> drawable.toBitmapOrDefault(96)
                            }
                        // 将bitmap转换为字节数组
                        val baos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                        iconBytes = baos.toByteArray()
                    } catch (e: Exception) {
                        Logger.w(TAG, "获取应用图标失败: ${appInfo.packageName}", e)
                    }

                    // 创建应用实体
                    val appEntity =
                        AppEntity(
                            packageName = packageName,
                            appName = appName,
                            isSystemApp = isSystemApp,
                            iconBytes = iconBytes,
                            isIconMissing = iconBytes == null,
                            lastUpdated = System.currentTimeMillis(),
                        )
                    appEntities.add(appEntity)

                    // 保存应用设备关联
                    val appDeviceEntity =
                        AppDeviceEntity(
                            packageName = packageName,
                            sourceDevice = "local",
                            lastUpdated = System.currentTimeMillis(),
                        )
                    appDeviceEntities.add(appDeviceEntity)
                } catch (e: Exception) {
                    Logger.w(TAG, "处理应用信息失败: ${appInfo.packageName}", e)
                }
            }

            // 批量保存应用到数据库
            if (appEntities.isNotEmpty()) {
                // 在保存应用之前，先获取所有现有的远程设备应用关联
                // 因为 saveApps 使用 OnConflictStrategy.REPLACE，会先删除再插入，触发外键级联删除
                val existingRemoteAssociations =
                    AppDatabaseHolder
                        .get()
                        ?.getAllAppDeviceAssociations()
                        ?.first()
                        ?.filter { it.sourceDevice != "local" } ?: emptyList()

                AppDatabaseHolder.get()?.saveApps(appEntities)

                // 重新保存远程设备的应用关联（本机的关联会在后面重新创建）
                if (existingRemoteAssociations.isNotEmpty()) {
                    AppDatabaseHolder.get()?.saveAppDeviceAssociations(existingRemoteAssociations)
                }
            }

            // 批量保存应用设备关联到数据库
            if (appDeviceEntities.isNotEmpty()) {
                AppDatabaseHolder.get()?.saveAppDeviceAssociations(appDeviceEntities)
            }

            // Logger.d(TAG, "应用列表加载成功，共 ${apps.size} 个应用")
        } catch (e: Exception) {
            Logger.e(TAG, "应用列表加载失败", e)
            _apps.value = emptyList()
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * 获取过滤后的应用列表。
     *
     * @param query 搜索关键字；若为空或仅空白字符则返回所有满足条件的应用。
     * @param showSystemApps 是否展示系统应用（true 包含系统应用，false 仅显示用户安装的应用）。
     * @param context Android 上下文，用于获取应用标签进行匹配（非空）。
     * @return 符合查询与系统/用户筛选条件的应用列表（不可为 null，可能为空）。
     */
    fun getFilteredApps(
        query: String,
        showSystemApps: Boolean,
        context: Context,
    ): List<ApplicationInfo> {
        val allApps = _apps.value
        if (allApps.isEmpty()) return emptyList()

        // 区分用户应用和系统应用
        val userApps =
            allApps.filter { app ->
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            }

        val displayApps = if (showSystemApps) allApps else userApps

        if (query.isBlank()) {
            return displayApps
        }

        // 搜索过滤
        return displayApps.filter { app ->
            try {
                val label = context.packageManager.getApplicationLabel(app).toString()
                val matchesLabel = label.contains(query, ignoreCase = true)
                val matchesPackage = app.packageName.contains(query, ignoreCase = true)
                matchesLabel || matchesPackage
            } catch (e: Exception) {
                Logger.w(TAG, "搜索时获取应用标签失败: ${app.packageName}", e)
                app.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    /**
     * 清除所有缓存（数据库缓存）。
     *
     * 说明：该方法会清空数据库中的应用与图标缓存。
     */
    suspend fun clearCache(context: Context) {
        AppDatabaseHolder.init(context)

        // 清除应用数据
        val apps = _apps.value
        apps.forEach {
            AppDatabaseHolder.get()?.deleteAppByPackageName(it.packageName)
        }

        // 清除远程应用列表
        RemoteAppsCache.clearRemoteApps()

        // 重置状态
        _apps.value = emptyList()
    }

    /**
     * 获取本机已安装和已缓存图标的包名集合。
     *
     * @param context Android 上下文，用于获取已安装应用列表和访问数据库
     * @return 已安装和已缓存图标的包名集合
     */
    suspend fun getInstalledAndCachedPackageNames(context: Context): Set<String> {
        AppDatabaseHolder.init(context)
        val installedPackages = getInstalledPackageNames(context)
        val cachedIconPackages = mutableSetOf<String>()
        // 从数据库获取所有应用包名
        val apps = AppDatabaseHolder.get()?.getAllApps()?.first() ?: emptyList()
        apps.forEach {
            cachedIconPackages.add(it.packageName)
        }
        return installedPackages + cachedIconPackages
    }

    /**
     * 检查应用数据（应用列表）是否已加载。
     *
     * @return 如果已加载返回 true，否则返回 false。
     */
    fun isDataLoaded(): Boolean {
        // 检查状态流是否有数据
        return _apps.value.isNotEmpty()
    }

    /**
     * 获取指定包名的应用标签（显示名）。
     *
     * @param context Android 上下文，用于访问 PackageManager（非空）。
     * @param packageName 目标应用的包名（非空）。
     * @return 应用的标签字符串；若无法获取则返回包名或空字符串，具体由 [AppListHelper.getApplicationLabel] 决定。
     */
    fun getAppLabel(
        context: Context,
        packageName: String,
    ): String = AppListHelper.getApplicationLabel(context, packageName)

    /**
     * 获取已安装应用包名集合（同步返回）。
     *
     * @param context Android 上下文（未使用，仅为 API 对称性保留）。
     * @return 当前已安装应用包名集合，若尚未加载返回空集合。
     */
    fun getInstalledPackageNames(context: Context): Set<String> = _apps.value.map { it.packageName }.toSet()

    /**
     * 异步获取已安装应用包名集合（确保在返回前数据已加载）。
     *
     * @param context Android 上下文，用于在必要时调用 [loadApps] 加载数据。
     * @return 已安装应用的包名集合（非空）。
     */
    suspend fun getInstalledPackageNamesAsync(context: Context): Set<String> {
        if (!isDataLoaded()) {
            loadApps(context)
        }
        return getInstalledPackageNames(context)
    }

    /**
     * 同步获取已安装应用包名集合。如果尚未加载，则会在当前线程同步加载数据（阻塞）。
     *
     * 注意：该方法会在必要时使用 runBlocking 在当前线程执行加载，请谨慎在 UI 线程中使用以避免卡顿。
     *
     * @param context Android 上下文，用于调用 [loadApps]。
     * @return 已安装应用的包名集合（非空）。
     */
    fun getInstalledPackageNamesSync(context: Context): Set<String> {
        if (!isDataLoaded()) {
            // 同步加载，使用runBlocking
            runBlocking {
                loadApps(context)
            }
        }
        return getInstalledPackageNames(context)
    }
}
