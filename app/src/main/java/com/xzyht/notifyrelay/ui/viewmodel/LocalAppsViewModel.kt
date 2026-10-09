package com.xzyht.notifyrelay.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notifyrelay.base.util.AppListHelper
import notifyrelay.base.util.InstalledAppsFilter

data class LocalAppInfo(
    val appName: String,
    val packageName: String,
    val isPinned: Boolean = false,
)

/**
 * 本地已安装应用列表的 ViewModel。
 *
 * 状态机与加载/搜索流程统一由 [AppsStateMachine] 承担，本类只提供数据源实现。
 */
class LocalAppsViewModel : ViewModel() {
    private val appsMachine =
        AppsStateMachine<LocalAppInfo, Unit> { context, _ ->
            withContext(Dispatchers.IO) {
                val packageManager = context.packageManager
                AppListHelper
                    .queryInstalledApplications(
                        context,
                        PackageManager.GET_META_DATA,
                        InstalledAppsFilter.LAUNCHABLE_USER_APPS,
                    ).map { appInfo ->
                        LocalAppInfo(
                            appName = appInfo.loadLabel(packageManager).toString(),
                            packageName = appInfo.packageName,
                        )
                    }.sortedBy { it.appName.lowercase() }
            }
        }

    val state: StateFlow<AppsState<LocalAppInfo>> = appsMachine.state

    fun loadApps(context: Context) {
        viewModelScope.launch { appsMachine.load(context, Unit) }
    }

    fun searchApps(query: String) = appsMachine.searchApps(query)
}
