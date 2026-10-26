package com.xzyht.notifyrelay.feature.appslist

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import notifyrelay.base.util.AppListHelper
import notifyrelay.domain.feature.appslist.model.LocalAppInfo
import notifyrelay.shared.feature.appslist.LocalAppsPort

/**
 * 以 [InstalledAppsRepository] 实现 [LocalAppsPort]。
 *
 * 界面的平台无关视图为 [LocalAppInfo]；本类负责转换 ApplicationInfo 并维护映射后的状态流。
 */
class AndroidLocalAppsPort(
    private val context: Context,
) : LocalAppsPort {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _apps = MutableStateFlow<List<LocalAppInfo>>(emptyList())
    override val apps: StateFlow<List<LocalAppInfo>> = _apps.asStateFlow()

    override val isLoading: StateFlow<Boolean> get() = InstalledAppsRepository.isLoading

    init {
        scope.launch {
            InstalledAppsRepository.apps.collect { list ->
                _apps.value = list.map { it.toLocalAppInfo() }
            }
        }
    }

    override suspend fun load() {
        InstalledAppsRepository.loadApps(context)
    }

    override fun filtered(
        query: String,
        showSystemApps: Boolean,
    ): List<LocalAppInfo> = _apps.value.filterBy(query, showSystemApps)

    override fun isDataLoaded(): Boolean = InstalledAppsRepository.isDataLoaded()

    override fun installedPackageNames(): Set<String> = InstalledAppsRepository.getInstalledPackageNames(context)

    override fun installedPackageNamesSync(): Set<String> = InstalledAppsRepository.getInstalledPackageNamesSync(context)

    private fun List<LocalAppInfo>.filterBy(
        query: String,
        showSystemApps: Boolean,
    ): List<LocalAppInfo> {
        val pool = if (showSystemApps) this else filter { !it.isSystemApp }
        if (query.isBlank()) return pool
        return pool.filter {
            it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }

    private fun ApplicationInfo.toLocalAppInfo(): LocalAppInfo =
        LocalAppInfo(
            packageName = packageName,
            appName = AppListHelper.getApplicationLabel(context, this),
            isSystemApp = (flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            isUpdatedSystemApp = (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
        )
}
