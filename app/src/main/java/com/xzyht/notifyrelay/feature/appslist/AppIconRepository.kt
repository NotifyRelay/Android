package com.xzyht.notifyrelay.feature.appslist

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import com.xzyht.notifyrelay.feature.appslist.sync.IconSyncManager
import com.xzyht.notifyrelay.feature.device.model.DeviceInfo
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import notifyrelay.base.util.Logger
import notifyrelay.data.database.entity.AppDeviceEntity
import notifyrelay.data.database.entity.AppEntity
import java.io.ByteArrayOutputStream

/**
 * 应用图标仓库。
 *
 * 负责应用图标的获取与持久化缓存，统一以 **PNG 字节数组** 形式存放在 `AppEntity.iconBytes`：
 * - 从 PackageManager 读取本地图标并写入数据库
 * - 从数据库读取图标（单个 / 批量 / 异步）
 * - 缓存外部（远端）应用图标并发布图标更新事件
 * - 本地与外部图标统一获取入口，支持缺失时自动向远端请求
 *
 * [AppRepository] 作为门面转发本 object 的公开方法，保持既有调用方不变。
 */
internal object AppIconRepository {
    private const val TAG = "AppIconRepository"

    // 图标更新事件流，用于通知UI层图标已更新
    private val _iconUpdates = MutableStateFlow<Pair<String, Long>?>(null)
    val iconUpdates: StateFlow<Pair<String, Long>?> = _iconUpdates.asStateFlow()

    /**
     * 通知UI层图标已更新
     * @param packageName 应用包名
     */
    fun notifyIconUpdated(packageName: String) {
        val updatedValue: Pair<String, Long> = Pair(packageName, System.currentTimeMillis())
        _iconUpdates.value = updatedValue
    }

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
    ): Bitmap? {
        AppDatabaseHolder.init(context)

        if (!InstalledAppsRepository.isDataLoaded()) {
            InstalledAppsRepository.loadApps(context)
        }

        // 从数据库获取应用信息
        val app = AppDatabaseHolder.get()?.getAppByPackageName(packageName)
        val iconBytes = app?.iconBytes
        if (iconBytes != null) {
            // 将字节数组转换为 Bitmap
            return BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
        }

        return null
    }

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
    ) {
        AppDatabaseHolder.init(context)

        // 转换图标为字节数组
        val iconBytes =
            if (icon != null) {
                val baos = ByteArrayOutputStream()
                icon.compress(Bitmap.CompressFormat.PNG, 100, baos)
                baos.toByteArray()
            } else {
                null
            }

        // 检查应用是否已存在
        val existingApp = AppDatabaseHolder.get()?.getAppByPackageName(packageName)
        val appEntity =
            if (existingApp != null) {
                // 更新现有应用
                existingApp.copy(
                    iconBytes = iconBytes,
                    isIconMissing = iconBytes == null,
                    lastUpdated = System.currentTimeMillis(),
                )
            } else {
                // 创建新应用
                AppEntity(
                    packageName = packageName,
                    appName = packageName, // 外部应用可能没有应用名，使用包名代替
                    isSystemApp = false,
                    iconBytes = iconBytes,
                    isIconMissing = iconBytes == null,
                    lastUpdated = System.currentTimeMillis(),
                )
            }

        // 保存应用到数据库
        AppDatabaseHolder.get()?.saveApp(appEntity)

        // 保存应用设备关联
        val appDeviceEntities = mutableListOf<AppDeviceEntity>()
        val appDeviceEntity =
            AppDeviceEntity(
                packageName = packageName,
                sourceDevice = deviceUuid,
                lastUpdated = System.currentTimeMillis(),
            )
        appDeviceEntities.add(appDeviceEntity)
        AppDatabaseHolder.get()?.saveAppDeviceAssociations(appDeviceEntities)

        // 通知UI层图标已更新
        _iconUpdates.value = Pair(packageName, System.currentTimeMillis())

        // Logger.d(TAG, "缓存外部应用图标: $packageName")
    }

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
    ): Bitmap? {
        AppDatabaseHolder.init(context)

        // 从数据库获取应用信息
        val app = AppDatabaseHolder.get()?.getAppByPackageName(packageName)
        val iconBytes = app?.iconBytes
        if (iconBytes != null) {
            // 将字节数组转换为 Bitmap
            return BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
        }

        return null
    }

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
    ): Map<String, Bitmap?> {
        AppDatabaseHolder.init(context)

        // 从数据库批量获取应用信息
        val apps = AppDatabaseHolder.get()?.getAppsByPackageNames(packageNames) ?: emptyList()
        val appMap = apps.associateBy { it.packageName }

        // 构建包名到图标的映射
        return packageNames.associateWith { packageName ->
            val app = appMap[packageName]
            val iconBytes = app?.iconBytes
            if (iconBytes != null) {
                // 将字节数组转换为 Bitmap
                BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
            } else {
                null
            }
        }
    }

    /**
     * 从 PackageManager 直接获取应用图标
     *
     * @param context Android 上下文，用于访问 PackageManager
     * @param packageName 目标应用的包名
     * @return 应用图标的 Bitmap；若不存在则返回 null
     */
    private suspend fun getAppIconFromPackageManager(
        context: Context,
        packageName: String,
    ): Bitmap? =
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val bitmap =
                when (val drawable = pm.getApplicationIcon(appInfo)) {
                    is BitmapDrawable -> drawable.bitmap
                    else -> {
                        // 将其他类型的drawable转换为bitmap
                        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
                        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
                        val createdBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(createdBitmap)
                        drawable.setBounds(0, 0, width, height)
                        drawable.draw(canvas)
                        createdBitmap
                    }
                }

            // 将获取到的图标缓存到数据库
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val iconBytes = baos.toByteArray()

            val existingApp = AppDatabaseHolder.get()?.getAppByPackageName(packageName)
            val appEntity =
                if (existingApp != null) {
                    existingApp.copy(
                        iconBytes = iconBytes,
                        isIconMissing = false,
                        lastUpdated = System.currentTimeMillis(),
                    )
                } else {
                    AppEntity(
                        packageName = packageName,
                        appName =
                            try {
                                pm.getApplicationLabel(appInfo).toString()
                            } catch (e: Exception) {
                                packageName
                            },
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        iconBytes = iconBytes,
                        isIconMissing = false,
                        lastUpdated = System.currentTimeMillis(),
                    )
                }
            AppDatabaseHolder.get()?.saveApp(appEntity)

            // 保存应用设备关联，使用 "local" 作为 sourceDevice
            val appDeviceEntity =
                AppDeviceEntity(
                    packageName = packageName,
                    sourceDevice = "local",
                    lastUpdated = System.currentTimeMillis(),
                )
            AppDatabaseHolder.get()?.saveAppDeviceAssociations(listOf(appDeviceEntity))

            bitmap
        } catch (e: Exception) {
            Logger.w(TAG, "从 PackageManager 获取应用图标失败: $packageName", e)
            null
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
        deviceManager: DeviceConnectionManager? = null,
        sourceDevice: DeviceInfo? = null,
    ): Bitmap? {
        try {
            AppDatabaseHolder.init(context)

            // 1. 从数据库获取应用图标
            val localIcon = getAppIconAsync(context, packageName)
            if (localIcon != null) {
                return localIcon
            }

            // 2. 尝试从 PackageManager 获取应用图标
            val packageIcon = getAppIconFromPackageManager(context, packageName)
            if (packageIcon != null) {
                return packageIcon
            }

            // 3. 自动请求缺失的图标
            if (deviceManager != null && sourceDevice != null) {
                IconSyncManager.checkAndSyncIcon(
                    context,
                    packageName,
                    deviceManager,
                    sourceDevice,
                )
            }

            return null
        } catch (e: Exception) {
            Logger.w(TAG, "获取应用图标失败: $packageName", e)
            return null
        }
    }
}
