package com.xzyht.notifyrelay.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xzyht.notifyrelay.feature.appslist.AppIconRepository
import com.xzyht.notifyrelay.feature.appslist.InstalledAppsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import notifyrelay.base.util.AppListHelper

/**
 * 应用选择弹窗的状态与查询承载。
 *
 * 界面只读取 Flow 并调用动作方法，不再直接访问 [InstalledAppsRepository] / [AppIconRepository]。
 */
class AppPickerViewModel : ViewModel() {
    val isLoading: StateFlow<Boolean> get() = InstalledAppsRepository.isLoading

    val apps: StateFlow<List<android.content.pm.ApplicationInfo>> get() = InstalledAppsRepository.apps

    val iconUpdateKey get() = AppIconRepository.iconUpdates

    fun appLabel(
        context: Context,
        packageName: String,
    ): String? = runCatching { AppListHelper.getApplicationLabel(context, packageName) }.getOrNull()

    fun filteredApps(
        query: String,
        showSystemApps: Boolean,
        context: Context,
    ) = InstalledAppsRepository.getFilteredApps(query, showSystemApps, context)

    fun isDataLoaded(): Boolean = InstalledAppsRepository.isDataLoaded()

    fun loadApps(context: Context) {
        viewModelScope.launch {
            InstalledAppsRepository.loadApps(context)
        }
    }

    fun loadIcon(
        context: Context,
        packageName: String,
        onResult: (android.graphics.Bitmap?) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(runCatching { AppIconRepository.getAppIconAsync(context, packageName) }.getOrNull())
        }
    }
}
