package com.xzyht.notifyrelay.feature.appslist

import android.content.Context
import android.content.pm.ApplicationInfo
import notifyrelay.base.util.Logger

/**
 * 已安装应用的过滤策略。
 *
 * 各策略的过滤条件互不包含，由调用点按自身需求显式选取。
 */
enum class InstalledAppsFilter {
    /** 不施加任何过滤。 */
    NONE,

    /** 排除系统应用、已更新的系统应用与自身包名。 */
    EXCLUDE_SYSTEM_AND_SELF,

    /** 仅排除系统应用，并要求存在可启动入口（Launcher 可见）。 */
    LAUNCHABLE_USER_APPS,
    ;

    /**
     * 判断单个应用是否满足本策略。
     *
     * @param context 用于取得 PackageManager 与自身包名的 Context
     * @param appInfo 待判定的应用信息
     * @return 满足本策略返回 true
     */
    fun accepts(
        context: Context,
        appInfo: ApplicationInfo,
    ): Boolean =
        when (this) {
            NONE -> true
            EXCLUDE_SYSTEM_AND_SELF ->
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 &&
                    appInfo.packageName != context.packageName
            LAUNCHABLE_USER_APPS ->
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    context.packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
        }
}

object AppListHelper {
    /**
     * 按策略枚举已安装应用。
     *
     * 本方法不吞异常，调用侧自行决定失败时的兜底值。
     *
     * @param context 用于访问 PackageManager 的 Context
     * @param flags 传给 `PackageManager.getInstalledApplications` 的标志位
     * @param filter 过滤策略
     * @return 满足策略的应用列表
     */
    fun queryInstalledApplications(
        context: Context,
        flags: Int,
        filter: InstalledAppsFilter,
    ): List<ApplicationInfo> =
        context.packageManager
            .getInstalledApplications(flags)
            .filter { filter.accepts(context, it) }

    /**
     * 获取已安装的应用列表（非系统应用且排除当前应用）
     *
     * @param context 用于访问 PackageManager 的 Context
     * @return 已安装应用的列表，若发生异常则返回空列表
     */
    fun getInstalledApplications(context: Context): List<ApplicationInfo> =
        try {
            queryInstalledApplications(context, 0, InstalledAppsFilter.EXCLUDE_SYSTEM_AND_SELF)
        } catch (e: Exception) {
            Logger.e("AppListHelper", "获取已安装应用列表失败: ${e.message}", e)
            emptyList()
        }

    /**
     * 检查是否可以查询应用列表
     *
     * @param context 用于访问 PackageManager 的 Context
     * @return 如果能够成功查询并且至少检测到多个应用返回 true，否则返回 false
     */
    fun canQueryApps(context: Context): Boolean =
        try {
            val apps = queryInstalledApplications(context, 0, InstalledAppsFilter.NONE)
            val result = apps.size > 2 // 简单的检查，至少有几个应用
            result
        } catch (e: Exception) {
            Logger.e("AppListHelper", "检查是否可查询应用列表失败: ${e.message}", e)
            false
        }

    /**
     * 获取应用标签（名称）
     *
     * @param context 用于访问 PackageManager 的 Context
     * @param packageName 要查询的应用包名
     * @return 应用的显示名称，若查询失败则返回 packageName
     */
    fun getApplicationLabel(
        context: Context,
        packageName: String,
    ): String =
        try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            getApplicationLabel(context, appInfo)
        } catch (e: Exception) {
            Logger.w("AppListHelper", "获取应用名失败, 包名=$packageName, 错误=${e.message}", e)
            packageName // 如果获取失败，返回包名
        }

    /**
     * 获取应用标签（名称）
     *
     * @param context 用于访问 PackageManager 的 Context
     * @param appInfo 已取得的应用信息
     * @return 应用的显示名称，若查询失败则返回 appInfo 的包名
     */
    fun getApplicationLabel(
        context: Context,
        appInfo: ApplicationInfo,
    ): String =
        try {
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            Logger.w("AppListHelper", "获取应用名失败, 包名=${appInfo.packageName}, 错误=${e.message}", e)
            appInfo.packageName // 如果获取失败，返回包名
        }
}
