package com.xzyht.notifyrelay.feature.platform

import android.content.Context
import com.xzyht.notifyrelay.feature.appslist.InstalledAppsRepository
import notifyrelay.base.util.AppListHelper
import notifyrelay.base.util.DeviceUtils
import notifyrelay.base.util.PermissionHelper
import notifyrelay.domain.feature.appslist.AppLabelPort
import notifyrelay.domain.feature.appslist.InstalledPackagesPort
import notifyrelay.domain.feature.notification.filter.NativeFilterPort
import notifyrelay.domain.platform.FormFactorPort

/** 设备形态端口：平板判定与系统版本均取 Android 系统能力。 */
class AndroidFormFactorPort(
    private val context: Context,
) : FormFactorPort {
    override fun isTablet(): Boolean = DeviceUtils.isTablet(context)

    override fun osVersion(): String? = PermissionHelper.getDetailedOsVersion()
}

/** 已安装应用包名端口：以 [InstalledAppsRepository] 为唯一来源。 */
class AndroidInstalledPackagesPort(
    private val context: Context,
) : InstalledPackagesPort {
    override suspend fun installedPackages(): Set<String> = InstalledAppsRepository.getInstalledPackageNames(context)

    override fun installedPackagesSync(): Set<String> = InstalledAppsRepository.getInstalledPackageNamesSync(context)
}

/** 本机应用显示名端口：经包管理器取应用名，失败回落包名。 */
class AndroidAppLabelPort(
    private val context: Context,
) : AppLabelPort {
    override fun labelOf(packageName: String): String = runCatching { AppListHelper.getApplicationLabel(context, packageName) }.getOrDefault(packageName)
}

/** Rust 过滤能力端口：以 NativeCore 的过滤三方法为唯一来源。 */
object AndroidNativeFilterPort : NativeFilterPort {
    override fun isReady(): Boolean =
        com.xzyht.notifyrelay.nativecore.NativeCore
            .isReady()

    override fun setFilterConfig(json: String): Boolean =
        com.xzyht.notifyrelay.nativecore.NativeCore
            .setFilterConfig(json) == 0

    override fun mapLocalPackage(packageName: String): String? =
        com.xzyht.notifyrelay.nativecore.NativeCore
            .mapLocalPackage(packageName)

    override fun checkFilterMode(
        packageName: String,
        notificationPackageName: String,
        title: String,
        text: String,
    ): Boolean =
        com.xzyht.notifyrelay.nativecore.NativeCore
            .checkFilterMode(packageName, notificationPackageName, title, text)
}
