package com.xzyht.notifyrelay.common.core.appslist

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.xzyht.notifyrelay.common.core.appslist.AppRepository.loadApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import notifyrelay.base.util.Logger
import notifyrelay.data.database.entity.AppDeviceEntity
import notifyrelay.data.database.entity.AppEntity
import notifyrelay.data.database.repository.DatabaseRepository
import java.io.ByteArrayOutputStream

/**
 * 应用数据仓库。
 *
 * 封装了应用列表和应用图标的内存缓存与持久化缓存操作，提供加载、过滤、查询、缓存管理等功能。
 * 所有对外提供的方法均设计为在主进程/UI 线程或协程中安全使用（按方法注释中的说明）。
 *
 * 功能概览：
 * - 加载已安装的应用列表并按应用名称排序
 * - 加载并缓存应用图标（内存 + 持久化）
 * - 提供同步/异步的图标与包名查询方法
 * - 清理和统计缓存
 */
object AppRepository {
    private const val TAG = "AppRepository"

    // 数据库仓库
    private lateinit var databaseRepository: DatabaseRepository

    // 状态流
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _apps = MutableStateFlow<List<ApplicationInfo>>(emptyList())
    val apps: StateFlow<List<ApplicationInfo>> = _apps.asStateFlow()

    private val _remoteApps = MutableStateFlow<Map<String, String>>(emptyMap())
    val remoteApps: StateFlow<Map<String, String>> = _remoteApps.asStateFlow()
    
    // 图标更新事件流，用于通知UI层图标已更新
    private val _iconUpdates = MutableStateFlow<String?>(null)
    val iconUpdates: StateFlow<String?> = _iconUpdates.asStateFlow()
    
    /**
     * 通知UI层图标已更新
     * @param packageName 应用包名
     */
    fun notifyIconUpdated(packageName: String) {
        _iconUpdates.value = packageName
    }
    
    // 初始化数据库仓库
    private fun initDatabaseRepository(context: Context) {
        if (!::databaseRepository.isInitialized) {
            databaseRepository = DatabaseRepository.getInstance(context)
        }
    }

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
        initDatabaseRepository(context)

        _isLoading.value = true
        try {
            //Logger.d(TAG, "开始加载应用列表")
            val apps = AppListHelper.getInstalledApplications(context).sortedBy { appInfo ->
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
            val pm = context.packageManager

            apps.forEach { appInfo ->
                try {
                    val packageName = appInfo.packageName
                    val appName = try {
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        packageName
                    }
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    // 获取应用图标
                    var iconBytes: ByteArray? = null
                    try {
                        val drawable = pm.getApplicationIcon(appInfo)
                        val bitmap = when (drawable) {
                            is android.graphics.drawable.BitmapDrawable -> drawable.bitmap
                            else -> {
                                // 将其他类型的drawable转换为bitmap
                                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
                                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
                                val createdBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(createdBitmap)
                                drawable.setBounds(0, 0, width, height)
                                drawable.draw(canvas)
                                createdBitmap
                            }
                        }
                        // 将bitmap转换为字节数组
                        val baos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                        iconBytes = baos.toByteArray()
                    } catch (e: Exception) {
                        Logger.w(TAG, "获取应用图标失败: ${appInfo.packageName}", e)
                    }

                    // 创建应用实体
                    val appEntity = AppEntity(
                        packageName = packageName,
                        appName = appName,
                        isSystemApp = isSystemApp,
                        iconBytes = iconBytes,
                        isIconMissing = iconBytes == null,
                        lastUpdated = System.currentTimeMillis()
                    )
                    appEntities.add(appEntity)

                    // 保存应用设备关联
                    val appDeviceEntity = AppDeviceEntity(
                        packageName = packageName,
                        sourceDevice = "local",
                        lastUpdated = System.currentTimeMillis()
                    )
                    databaseRepository.saveAppDeviceAssociation(appDeviceEntity)
                } catch (e: Exception) {
                    Logger.w(TAG, "处理应用信息失败: ${appInfo.packageName}", e)
                }
            }

            // 批量保存应用到数据库
            if (appEntities.isNotEmpty()) {
                databaseRepository.saveApps(appEntities)
            }

            //Logger.d(TAG, "应用列表加载成功，共 ${apps.size} 个应用")
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
        context: Context
    ): List<ApplicationInfo> {
        val allApps = _apps.value
        if (allApps.isEmpty()) return emptyList()

        // 区分用户应用和系统应用
        val userApps = allApps.filter { app ->
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
        initDatabaseRepository(context)

        // 清除应用数据
        val apps = _apps.value
        apps.forEach {
            databaseRepository.deleteAppByPackageName(it.packageName)
        }

        // 清除远程应用列表
        _remoteApps.value = emptyMap()

        // 重置状态
        _apps.value = emptyList()
    }

    /**
     * 缓存远程应用列表。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @param apps 远程应用列表，格式为 Map<包名, 应用名>
     * @param deviceUuid 远程设备UUID
     */
    suspend fun cacheRemoteAppList(context: Context, apps: Map<String, String>, deviceUuid: String) {
        initDatabaseRepository(context)

        val appEntities = mutableListOf<AppEntity>()
        val appDeviceEntities = mutableListOf<AppDeviceEntity>()

        apps.forEach { (packageName, appName) ->
            // 检查应用是否已存在
            val existingApp = databaseRepository.getAppByPackageName(packageName)
            val appEntity = if (existingApp != null) {
                // 更新现有应用
                existingApp.copy(
                    appName = appName,
                    lastUpdated = System.currentTimeMillis()
                )
            } else {
                // 创建新应用
                AppEntity(
                    packageName = packageName,
                    appName = appName,
                    isSystemApp = false,
                    iconBytes = null,
                    isIconMissing = true,
                    lastUpdated = System.currentTimeMillis()
                )
            }
            appEntities.add(appEntity)

            // 创建应用设备关联
            val appDeviceEntity = AppDeviceEntity(
                packageName = packageName,
                sourceDevice = deviceUuid,
                lastUpdated = System.currentTimeMillis()
            )
            appDeviceEntities.add(appDeviceEntity)
        }

        // 保存到数据库
        if (appEntities.isNotEmpty()) {
            databaseRepository.saveApps(appEntities)
        }
        if (appDeviceEntities.isNotEmpty()) {
            databaseRepository.saveAppDeviceAssociations(appDeviceEntities)
        }

        _remoteApps.value = apps
        //Logger.d(TAG, "缓存远程应用列表成功，共 ${apps.size} 个应用")
    }

    /**
     * 获取指定包名列表中缺失的图标。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @param packageNames 要检查的包名列表
     * @return 缺失图标的包名列表
     */
    suspend fun getMissingIconsForPackages(context: Context, packageNames: List<String>): List<String> {
        initDatabaseRepository(context)

        return packageNames.filter { pkg ->
            val app = databaseRepository.getAppByPackageName(pkg)
            app?.isIconMissing ?: true
        }
    }
    
    /**
     * 标记一个图标为缺失。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @param packageName 要标记的包名
     */
    suspend fun markIconAsMissing(context: Context, packageName: String) {
        initDatabaseRepository(context)
        databaseRepository.markAppIconAsMissing(packageName)
        //Logger.d(TAG, "标记图标为缺失：$packageName")
    }
    
    /**
     * 检查一个图标是否被标记为缺失。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @param packageName 要检查的包名
     * @return 如果被标记为缺失则返回 true，否则返回 false
     */
    suspend fun isIconMarkedMissing(context: Context, packageName: String): Boolean {
        initDatabaseRepository(context)
        val app = databaseRepository.getAppByPackageName(packageName)
        return app?.isIconMissing ?: true
    }
    
    /**
     * 清除缺失图标记录。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     */
    suspend fun clearMissingIcons(context: Context) {
        initDatabaseRepository(context)
        // 这里可以实现批量更新所有缺失图标记录为非缺失
        // 暂时留空，因为数据库设计中每个应用的图标缺失状态是独立的
        //Logger.d(TAG, "清除所有缺失图标记录")
    }
    
    /**
     * 清除指定包名的缺失图标记录。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @param packageName 要清除记录的包名
     */
    suspend fun clearMissingIcon(context: Context, packageName: String) {
        initDatabaseRepository(context)
        // 这里可以实现更新指定应用的图标缺失状态为非缺失
        // 暂时留空，因为当图标被更新时，isIconMissing 会自动设置为 false
        //Logger.d(TAG, "清除缺失图标记录：$packageName")
    }
    
    /**
     * 将应用包名与设备UUID关联。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @param packageName 应用包名
     * @param deviceUuid 设备UUID
     */
    suspend fun associateAppWithDevice(context: Context, packageName: String, deviceUuid: String) {
        initDatabaseRepository(context)
        val appDeviceEntity = AppDeviceEntity(
            packageName = packageName,
            sourceDevice = deviceUuid,
            lastUpdated = System.currentTimeMillis()
        )
        databaseRepository.saveAppDeviceAssociation(appDeviceEntity)
        //Logger.d(TAG, "关联应用包名与设备：$packageName -> $deviceUuid")
    }
    
    /**
     * 获取应用包名的来源设备UUID。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @param packageName 应用包名
     * @return 设备UUID列表，如果没有关联则返回空列表
     */
    suspend fun getAppDeviceUuids(context: Context, packageName: String): List<String> {
        initDatabaseRepository(context)
        val appDevices = databaseRepository.getAppDevicesByPackageName(packageName).first()
        return appDevices.map { it.sourceDevice }
    }
    
    /**
     * 清除应用包名与设备的关联。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @param packageName 应用包名
     */
    suspend fun clearAppDeviceAssociation(context: Context, packageName: String) {
        initDatabaseRepository(context)
        databaseRepository.deleteAppDeviceAssociationsByPackageName(packageName)
        //Logger.d(TAG, "清除应用包名与设备的关联：$packageName")
    }
    
    /**
     * 批量将应用包名与设备UUID关联。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @param packageNames 应用包名列表
     * @param deviceUuid 设备UUID
     */
    suspend fun associateAppsWithDevice(context: Context, packageNames: List<String>, deviceUuid: String) {
        initDatabaseRepository(context)
        val appDeviceEntities = mutableListOf<AppDeviceEntity>()
        packageNames.forEach {
            val appDeviceEntity = AppDeviceEntity(
                packageName = it,
                sourceDevice = deviceUuid,
                lastUpdated = System.currentTimeMillis()
            )
            appDeviceEntities.add(appDeviceEntity)
        }
        databaseRepository.saveAppDeviceAssociations(appDeviceEntities)
        //Logger.d(TAG, "批量关联应用包名与设备：${packageNames.size} 个应用 -> $deviceUuid")
    }

    /**
     * 获取本机已安装和已缓存图标的包名集合。
     *
     * @param context Android 上下文，用于获取已安装应用列表和访问数据库
     * @return 已安装和已缓存图标的包名集合
     */
    suspend fun getInstalledAndCachedPackageNames(context: Context): Set<String> {
        initDatabaseRepository(context)
        val installedPackages = getInstalledPackageNames(context)
        val cachedIconPackages = mutableSetOf<String>()
        // 从数据库获取所有应用包名
        val apps = databaseRepository.getAllApps().first()
        apps.forEach {
            cachedIconPackages.add(it.packageName)
        }
        return installedPackages + cachedIconPackages
    }

    /**
     * 获取所有已缓存图标的包名。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @return 已缓存图标的包名集合
     */
    suspend fun getAllCachedIconKeys(context: Context): Set<String> {
        initDatabaseRepository(context)
        val cachedIconPackages = mutableSetOf<String>()
        // 从数据库获取所有应用包名
        val apps = databaseRepository.getAllApps().first()
        apps.forEach {
            if (it.iconBytes != null) {
                cachedIconPackages.add(it.packageName)
            }
        }
        return cachedIconPackages
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
    fun getAppLabel(context: Context, packageName: String): String {
        return AppListHelper.getApplicationLabel(context, packageName)
    }

    /**
     * 获取已安装应用包名集合（同步返回）。
     *
     * @param context Android 上下文（未使用，仅为 API 对称性保留）。
     * @return 当前已安装应用包名集合，若尚未加载返回空集合。
     */
    fun getInstalledPackageNames(context: Context): Set<String> {
        return _apps.value.map { it.packageName }.toSet()
    }

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
            kotlinx.coroutines.runBlocking {
                loadApps(context)
            }
        }
        return getInstalledPackageNames(context)
    }

    /**
     * 加载并缓存应用图标（仅持久化）。
     *
     * 说明：该方法为挂起函数，会从 PackageManager 获取图标并保存到数据库。
     *
     * @param context Android 上下文，用于访问 PackageManager 与数据库（非空）。
     * @param apps 需要加载图标的应用列表（非空，可为空列表）。
     */
    private suspend fun loadAppIcons(context: Context, apps: List<ApplicationInfo>) {
        try {
            //Logger.d(TAG, "开始加载应用图标")
            val pm = context.packageManager

            apps.forEach { appInfo ->
                try {
                    val packageName = appInfo.packageName

                    // 从PackageManager获取图标
                    val drawable = pm.getApplicationIcon(appInfo)
                    val bitmap = when (drawable) {
                        is android.graphics.drawable.BitmapDrawable -> drawable.bitmap
                        else -> {
                            // 将其他类型的drawable转换为bitmap
                            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
                            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
                            val createdBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(createdBitmap)
                            drawable.setBounds(0, 0, width, height)
                            drawable.draw(canvas)
                            createdBitmap
                        }
                    }

                    // 转换为字节数组
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    val iconBytes = baos.toByteArray()

                    // 更新数据库中的图标
                    val existingApp = databaseRepository.getAppByPackageName(packageName)
                    if (existingApp != null) {
                        val updatedApp = existingApp.copy(
                            iconBytes = iconBytes,
                            isIconMissing = false,
                            lastUpdated = System.currentTimeMillis()
                        )
                        databaseRepository.saveApp(updatedApp)
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "获取应用图标失败: ${appInfo.packageName}", e)
                }
            }

            //Logger.d(TAG, "应用图标加载成功，共 ${apps.size} 个图标")
        } catch (e: Exception) {
            Logger.e(TAG, "应用图标加载失败", e)
        }
    }

    /**
     * 获取应用图标（非阻塞版本）。
     *
     * 说明：该方法为非阻塞的同步接口，直接返回 null，
     *       调用方可使用 [getAppIconAsync] 获取图标的异步版本。
     *
     * @param packageName 目标应用的包名（非空）。
     * @return 始终返回 null，让调用方使用异步版本。
     */
    fun getAppIcon(packageName: String): Bitmap? {
        return null // 让调用方使用异步版本
    }

    /**
     * 异步获取应用图标（确保在返回前数据已加载）。
     *
     * @param context Android 上下文，用于在必要时加载应用列表与访问数据库。
     * @param packageName 目标应用的包名（非空）。
     * @return 应用图标的 Bitmap；若不存在则返回 null。
     */
    suspend fun getAppIconAsync(context: Context, packageName: String): Bitmap? {
        initDatabaseRepository(context)

        if (!isDataLoaded()) {
            loadApps(context)
        }

        // 从数据库获取应用信息
        val app = databaseRepository.getAppByPackageName(packageName)
        val iconBytes = app?.iconBytes
        if (iconBytes != null) {
            // 将字节数组转换为 Bitmap
            return BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
        }

        return null
    }

    /**
     * 同步获取应用图标（若未加载应用列表则会同步加载，可能阻塞当前线程）。
     *
     * 注意：该方法在必要时会使用 runBlocking 加载数据，请谨慎在 UI 线程中直接调用以避免卡顿。
     *
     * @param context Android 上下文，用于加载应用列表与访问数据库。
     * @param packageName 目标应用的包名（非空）。
     * @return 应用图标的 Bitmap；若不存在则返回 null。
     */
    fun getAppIconSync(context: Context, packageName: String): Bitmap? {
        initDatabaseRepository(context)

        if (!isDataLoaded()) {
            // 同步加载，使用runBlocking
            kotlinx.coroutines.runBlocking {
                loadApps(context)
            }
        }

        // 从数据库获取应用信息
        val app = kotlinx.coroutines.runBlocking {
            databaseRepository.getAppByPackageName(packageName)
        }
        val iconBytes = app?.iconBytes
        if (iconBytes != null) {
            // 将字节数组转换为 Bitmap
            return BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
        }

        return null
    }

    /**
     * 检查图标是否已加载完成。
     *
     * @return 若图标加载过程已完成返回 true，否则返回 false。
     */
    fun isIconsLoaded(): Boolean {
        // 由于我们现在使用数据库存储图标，图标加载是按需进行的
        // 所以这里总是返回 true，表示图标系统已准备就绪
        return true
    }

    /**
     * 获取所有已缓存的图标（只读拷贝）。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @return 包名 -> Bitmap 的映射拷贝，Bitmap 可能为 null 表示缓存占位或失败。
     */
    suspend fun getAllCachedIcons(context: Context): Map<String, Bitmap?> {
        initDatabaseRepository(context)
        val iconsMap = mutableMapOf<String, Bitmap?>()
        
        // 从数据库获取所有应用
        val apps = databaseRepository.getAllApps().first()
        for (app in apps) {
            val iconBytes = app.iconBytes
            if (iconBytes != null) {
                try {
                    val bitmap = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
                    iconsMap[app.packageName] = bitmap
                } catch (e: Exception) {
                    Logger.w(TAG, "解码图标失败: ${app.packageName}", e)
                    iconsMap[app.packageName] = null
                }
            } else {
                iconsMap[app.packageName] = null
            }
        }
        
        return iconsMap
    }

    /**
     * 缓存外部应用的图标（数据库存储），用于保存未安装应用或来自远端的图标数据。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @param packageName 外部应用的包名（用于作为键）。
     * @param icon 要缓存的 Bitmap，若为 null 则只在数据库中移除对应条目。
     * @param deviceUuid 设备UUID，用于关联应用与设备
     */
    suspend fun cacheExternalAppIcon(context: Context, packageName: String, icon: Bitmap?, deviceUuid: String) {
        initDatabaseRepository(context)
        
        // 转换图标为字节数组
        val iconBytes = if (icon != null) {
            val baos = ByteArrayOutputStream()
            icon.compress(Bitmap.CompressFormat.PNG, 100, baos)
            baos.toByteArray()
        } else {
            null
        }
        
        // 检查应用是否已存在
        val existingApp = databaseRepository.getAppByPackageName(packageName)
        val appEntity = if (existingApp != null) {
            // 更新现有应用
            existingApp.copy(
                iconBytes = iconBytes,
                isIconMissing = iconBytes == null,
                lastUpdated = System.currentTimeMillis()
            )
        } else {
            // 创建新应用
            AppEntity(
                packageName = packageName,
                appName = packageName, // 外部应用可能没有应用名，使用包名代替
                isSystemApp = false,
                iconBytes = iconBytes,
                isIconMissing = iconBytes == null,
                lastUpdated = System.currentTimeMillis()
            )
        }
        
        // 保存应用到数据库
        databaseRepository.saveApp(appEntity)
        
        // 保存应用设备关联
        val appDeviceEntity = AppDeviceEntity(
            packageName = packageName,
            sourceDevice = deviceUuid,
            lastUpdated = System.currentTimeMillis()
        )
        databaseRepository.saveAppDeviceAssociation(appDeviceEntity)
        
        // 通知UI层图标已更新
        _iconUpdates.value = packageName

        //Logger.d(TAG, "缓存外部应用图标: $packageName")
    }

    /**
     * 移除外部应用图标的缓存（数据库存储）。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @param packageName 目标包名，会从数据库中移除对应记录。
     */
    suspend fun removeExternalAppIcon(context: Context, packageName: String) {
        initDatabaseRepository(context)
        
        // 从数据库删除应用
        databaseRepository.deleteAppByPackageName(packageName)

        //Logger.d(TAG, "移除外部应用图标缓存: $packageName")
    }

    /**
     * 获取外部应用图标（从数据库加载）。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @param packageName 目标应用包名。
     * @return 若存在则返回 Bitmap，否则返回 null。
     */
    suspend fun getExternalAppIcon(context: Context, packageName: String): Bitmap? {
        initDatabaseRepository(context)
        
        // 从数据库获取应用信息
        val app = databaseRepository.getAppByPackageName(packageName)
        val iconBytes = app?.iconBytes
        if (iconBytes != null) {
            // 将字节数组转换为 Bitmap
            return BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
        }

        return null
    }
    
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
        deviceManager: com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager? = null,
        sourceDevice: com.xzyht.notifyrelay.feature.device.service.DeviceInfo? = null
    ): Bitmap? {
        try {
            // 1. 优先获取本地应用图标
            val localIcon = getAppIconSync(context, packageName)
            if (localIcon != null) {
                return localIcon
            }
            
            // 2. 尝试获取外部应用图标
            val externalIcon = getExternalAppIcon(context, packageName)
            if (externalIcon != null) {
                return externalIcon
            }
            
            // 3. 自动请求缺失的图标
            if (deviceManager != null && sourceDevice != null) {
                com.xzyht.notifyrelay.common.core.sync.IconSyncManager.checkAndSyncIcon(
                    context,
                    packageName,
                    deviceManager,
                    sourceDevice
                )
            }
            
            return null
        } catch (e: Exception) {
            //Logger.e(TAG, "获取应用图标失败: $packageName", e)
            return null
        }
    }
    
    /**
     * 异步获取应用图标，自动处理本地和外部应用，并支持自动请求缺失的图标。
     *
     * @param context 上下文
     * @param packageName 应用包名
     * @param deviceManager 设备连接管理器（可选，用于自动请求图标）
     * @param sourceDevice 源设备信息（可选，用于自动请求图标）
     * @return 应用图标，若无法获取则返回 null
     */
    suspend fun getAppIconWithAutoRequestAsync(
        context: Context,
        packageName: String,
        deviceManager: com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager? = null,
        sourceDevice: com.xzyht.notifyrelay.feature.device.service.DeviceInfo? = null
    ): Bitmap? {
        try {
            // 1. 优先获取本地应用图标
            val localIcon = getAppIconAsync(context, packageName)
            if (localIcon != null) {
                return localIcon
            }
            
            // 2. 尝试获取外部应用图标
            val externalIcon = getExternalAppIcon(context, packageName)
            if (externalIcon != null) {
                return externalIcon
            }
            
            // 3. 自动请求缺失的图标
            if (deviceManager != null && sourceDevice != null) {
                com.xzyht.notifyrelay.common.core.sync.IconSyncManager.checkAndSyncIcon(
                    context,
                    packageName,
                    deviceManager,
                    sourceDevice
                )
            }
            
            return null
        } catch (e: Exception) {
            //Logger.e(TAG, "异步获取应用图标失败: $packageName", e)
            return null
        }
    }

    /**
     * 获取持久化图标缓存的统计信息。
     *
     * @param context Android 上下文，用于访问数据库（非空）。
     * @return 包含缓存大小、条目数等统计信息。
     */
    suspend fun getCacheStats(context: Context): Map<String, Any> {
        initDatabaseRepository(context)
        
        // 从数据库获取所有应用
        val apps = databaseRepository.getAllApps().first()
        val totalApps = apps.size
        val appsWithIcons = apps.count { app -> app.iconBytes != null }
        val totalIconSize = apps.sumOf { app -> app.iconBytes?.size?.toLong() ?: 0L }
        
        return mapOf(
            "totalApps" to totalApps,
            "appsWithIcons" to appsWithIcons,
            "totalIconSizeBytes" to totalIconSize,
            "totalIconSizeMB" to totalIconSize / (1024.0 * 1024.0)
        )
    }

    /**
     * 清理过期的持久化图标缓存（挂起函数）。
     *
     * 说明：会删除满足过期策略的缓存文件/记录以释放磁盘空间。
     */
    suspend fun cleanupExpiredCache(context: Context) {
        initDatabaseRepository(context)
        
        // 计算过期时间（30天前）
        val expiryTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        
        // 获取过期的应用数据
        val expiredApps = databaseRepository.getExpiredApps(expiryTime)
        
        // 删除过期的应用数据
        expiredApps.forEach {
            databaseRepository.deleteAppByPackageName(it.packageName)
        }
    }
}
