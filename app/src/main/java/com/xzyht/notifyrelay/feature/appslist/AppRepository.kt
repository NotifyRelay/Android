package com.xzyht.notifyrelay.feature.appslist

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import com.xzyht.notifyrelay.feature.appslist.AppRepository.loadApps
import com.xzyht.notifyrelay.feature.appslist.model.RemoteAppInfo
import com.xzyht.notifyrelay.feature.device.model.DeviceInfo
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import kotlinx.coroutines.flow.StateFlow

/**
 * 应用数据仓库（门面）。
 *
 * 封装了应用列表和应用图标的内存缓存与持久化缓存操作，提供加载、过滤、查询、缓存管理等功能。
 * 所有对外提供的方法均设计为在主进程/UI 线程或协程中安全使用（按方法注释中的说明）。
 *
 * 本 object 已按职责拆分为多个子仓库，仅保留公开 API 作为门面转发，调用方无需改动：
 * - [InstalledAppsRepository]：已安装应用加载、过滤与查询
 * - [AppIconRepository]：图标获取与持久化缓存
 * - [PinnedAppsRepository]：置顶应用状态与持久化
 * - [RemoteAppsCache]：远程应用列表缓存
 *
 * 功能概览：
 * - 加载已安装的应用列表并按应用名称排序
 * - 加载并缓存应用图标（内存 + 持久化）
 * - 提供同步/异步的图标与包名查询方法
 * - 清理和统计缓存
 */
object AppRepository {
    // 状态流
    val isLoading: StateFlow<Boolean> = InstalledAppsRepository.isLoading

    val apps: StateFlow<List<ApplicationInfo>> = InstalledAppsRepository.apps

    val remoteApps: StateFlow<Map<String, String>> = RemoteAppsCache.remoteApps

    // 图标更新事件流，用于通知UI层图标已更新
    val iconUpdates: StateFlow<Pair<String, Long>?> = AppIconRepository.iconUpdates

    /**
     * 通知UI层图标已更新
     * @param packageName 应用包名
     */
    fun notifyIconUpdated(packageName: String) = AppIconRepository.notifyIconUpdated(packageName)

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
    suspend fun loadApps(context: Context) = InstalledAppsRepository.loadApps(context)

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
    ): List<ApplicationInfo> = InstalledAppsRepository.getFilteredApps(query, showSystemApps, context)

    /**
     * 清除所有缓存（数据库缓存）。
     *
     * 说明：该方法会清空数据库中的应用与图标缓存。
     */
    suspend fun clearCache(context: Context) = InstalledAppsRepository.clearCache(context)

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
    ) = RemoteAppsCache.cacheRemoteAppList(context, apps, deviceUuid)

    /**
     * 获取本机已安装和已缓存图标的包名集合。
     *
     * @param context Android 上下文，用于获取已安装应用列表和访问数据库
     * @return 已安装和已缓存图标的包名集合
     */
    suspend fun getInstalledAndCachedPackageNames(context: Context): Set<String> = InstalledAppsRepository.getInstalledAndCachedPackageNames(context)

    /**
     * 检查应用数据（应用列表）是否已加载。
     *
     * @return 如果已加载返回 true，否则返回 false。
     */
    fun isDataLoaded(): Boolean = InstalledAppsRepository.isDataLoaded()

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
    ): String = InstalledAppsRepository.getAppLabel(context, packageName)

    /**
     * 获取已安装应用包名集合（同步返回）。
     *
     * @param context Android 上下文（未使用，仅为 API 对称性保留）。
     * @return 当前已安装应用包名集合，若尚未加载返回空集合。
     */
    fun getInstalledPackageNames(context: Context): Set<String> = InstalledAppsRepository.getInstalledPackageNames(context)

    /**
     * 异步获取已安装应用包名集合（确保在返回前数据已加载）。
     *
     * @param context Android 上下文，用于在必要时调用 [loadApps] 加载数据。
     * @return 已安装应用的包名集合（非空）。
     */
    suspend fun getInstalledPackageNamesAsync(context: Context): Set<String> = InstalledAppsRepository.getInstalledPackageNamesAsync(context)

    /**
     * 同步获取已安装应用包名集合。如果尚未加载，则会在当前线程同步加载数据（阻塞）。
     *
     * 注意：该方法会在必要时使用 runBlocking 在当前线程执行加载，请谨慎在 UI 线程中使用以避免卡顿。
     *
     * @param context Android 上下文，用于调用 [loadApps]。
     * @return 已安装应用的包名集合（非空）。
     */
    fun getInstalledPackageNamesSync(context: Context): Set<String> = InstalledAppsRepository.getInstalledPackageNamesSync(context)

    /**
     * 异步获取应用图标（确保在返回前数据已加载）。
     *
     * @param context Android 上下文，用于在必要时加载应用列表与访问数据库。
     * @param packageName 目标应用的包名（非空）。
     * @return 应用图标的 Bitmap；若不存在则返回 null。
     */
    suspend fun getAppIconAsync(
        context: Context,
        packageName: String,
    ): Bitmap? = AppIconRepository.getAppIconAsync(context, packageName)

    /**
     * 缓存外部应用的图标（数据库存储），用于保存未安装应用或来自远端的图标数据。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @param packageName 外部应用的包名（用于作为键）。
     * @param icon 要缓存的 Bitmap，若为 null 则只在数据库中移除对应条目。
     * @param deviceUuid 设备UUID，用于关联应用与设备
     */
    suspend fun cacheExternalAppIcon(
        context: Context,
        packageName: String,
        icon: Bitmap?,
        deviceUuid: String,
    ) = AppIconRepository.cacheExternalAppIcon(context, packageName, icon, deviceUuid)

    /**
     * 获取外部应用图标（从数据库加载）。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @param packageName 目标应用包名。
     * @return 若存在则返回 Bitmap，否则返回 null。
     */
    suspend fun getExternalAppIcon(
        context: Context,
        packageName: String,
    ): Bitmap? = AppIconRepository.getExternalAppIcon(context, packageName)

    /**
     * 批量获取外部应用图标（从数据库加载）。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @param packageNames 目标应用包名列表。
     * @return 包名到图标的映射，若不存在则对应值为 null。
     */
    suspend fun getExternalAppIcons(
        context: Context,
        packageNames: List<String>,
    ): Map<String, Bitmap?> = AppIconRepository.getExternalAppIcons(context, packageNames)

    /**
     * 统一获取应用图标，自动处理本地和外部应用，并支持自动请求缺失的图标。
     *
     * @param context 上下文
     * @param packageName 应用包名
     * @param deviceManager 设备连接管理器（可选，用于自动请求图标）
     * @param sourceDevice 源设备信息（可选，用于自动请求图标）
     * @return 应用图标，若无法获取则返回 null
     */
    suspend fun getAppIconWithAutoRequest(
        context: Context,
        packageName: String,
        deviceManager: DeviceConnectionManager? = null,
        sourceDevice: DeviceInfo? = null,
    ): Bitmap? = AppIconRepository.getAppIconWithAutoRequest(context, packageName, deviceManager, sourceDevice)

    // 置顶应用状态与持久化由 PinnedAppsRepository 统一持有，此处仅作为门面转发。
    val pinnedApps: StateFlow<Map<String, Set<String>>> = PinnedAppsRepository.pinnedApps

    /**
     * 从 SharedPreferences 载入指定设备的置顶应用集合。
     *
     * @param context Android 上下文，用于访问 SharedPreferences。
     * @param deviceUuid 设备 UUID。
     */
    fun loadPinnedApps(
        context: Context,
        deviceUuid: String,
    ) = PinnedAppsRepository.loadPinnedApps(context, deviceUuid)

    /**
     * 置顶指定设备的某个应用（内存 + SharedPreferences 双写）。
     *
     * @param context Android 上下文，用于访问 SharedPreferences。
     * @param deviceUuid 设备 UUID。
     * @param packageName 目标应用包名。
     */
    fun pinApp(
        context: Context,
        deviceUuid: String,
        packageName: String,
    ) = PinnedAppsRepository.pinApp(context, deviceUuid, packageName)

    /**
     * 取消置顶指定设备的某个应用（内存 + SharedPreferences 双写）。
     *
     * @param context Android 上下文，用于访问 SharedPreferences。
     * @param deviceUuid 设备 UUID。
     * @param packageName 目标应用包名。
     */
    fun unpinApp(
        context: Context,
        deviceUuid: String,
        packageName: String,
    ) = PinnedAppsRepository.unpinApp(context, deviceUuid, packageName)

    /**
     * 判断指定设备的某个应用是否已置顶。
     *
     * @param deviceUuid 设备 UUID。
     * @param packageName 目标应用包名。
     * @return 已置顶返回 true，否则返回 false。
     */
    fun isAppPinned(
        deviceUuid: String,
        packageName: String,
    ): Boolean = PinnedAppsRepository.isAppPinned(deviceUuid, packageName)

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
    ): List<RemoteAppInfo> = RemoteAppsCache.getRemoteAppsList(context, deviceUuid)
}
