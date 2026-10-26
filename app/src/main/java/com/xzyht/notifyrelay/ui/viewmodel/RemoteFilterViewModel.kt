package com.xzyht.notifyrelay.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xzyht.notifyrelay.feature.appslist.AppIconRepository
import com.xzyht.notifyrelay.feature.appslist.InstalledAppsRepository
import com.xzyht.notifyrelay.feature.notification.filter.RemoteFilterConfig
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 远程过滤设置的状态与操作承载。
 *
 * 界面只读取状态并调用动作方法，不再直接访问 [RemoteFilterConfig] / [InstalledAppsRepository] / [AppIconRepository]。
 */
class RemoteFilterViewModel : ViewModel() {
    var filterMode by mutableStateOf(RemoteFilterConfig.filterMode)
        private set

    var enableDedup by mutableStateOf(RemoteFilterConfig.enableDeduplication)
        private set

    var enablePackageGroupMapping by mutableStateOf(RemoteFilterConfig.enablePackageGroupMapping)
        private set

    var enableLockScreenOnly by mutableStateOf(RemoteFilterConfig.enableLockScreenOnly)
        private set

    var allGroups by mutableStateOf<List<MutableList<String>>>(emptyList())
        private set

    var allGroupEnabled by mutableStateOf<MutableList<Boolean>>(mutableListOf())
        private set

    var filterItems by mutableStateOf<List<Pair<String, String?>>>(emptyList())
        private set

    var enabledKeys by mutableStateOf<Set<String>>(emptySet())
        private set

    val installedPkgs: StateFlow<List<android.content.pm.ApplicationInfo>> get() = InstalledAppsRepository.apps

    val defaultGroupSize: Int get() = RemoteFilterConfig.defaultPackageGroups.size

    fun load(context: Context) {
        viewModelScope.launch {
            if (!RemoteFilterConfig.isLoaded) {
                RemoteFilterConfig.load(context)
                RemoteFilterConfig.isLoaded = true
            }
            syncFromConfig()
        }
    }

    /** 从 [RemoteFilterConfig] 同步全部状态到本 ViewModel。 */
    fun syncFromConfig() {
        filterMode = RemoteFilterConfig.filterMode
        enableDedup = RemoteFilterConfig.enableDeduplication
        enablePackageGroupMapping = RemoteFilterConfig.enablePackageGroupMapping
        enableLockScreenOnly = RemoteFilterConfig.enableLockScreenOnly
        allGroups =
            RemoteFilterConfig.defaultPackageGroups.map { it.toMutableList() } +
            RemoteFilterConfig.customPackageGroups.map { it.toMutableList() }
        allGroupEnabled =
            (RemoteFilterConfig.defaultGroupEnabled + RemoteFilterConfig.customGroupEnabled).toMutableList()
        filterItems = RemoteFilterConfig.getActiveFilterList()
        refreshEnabledKeys()
    }

    private fun refreshEnabledKeys() {
        enabledKeys =
            filterItems
                .filter { (pkg, kw) -> RemoteFilterConfig.isActiveEntryEnabled(pkg, kw ?: "") }
                .map { (pkg, kw) -> pkg + "|" + (kw ?: "") }
                .toSet()
    }

    fun setFilterMode(
        context: Context,
        value: String,
    ) {
        filterMode = value
        RemoteFilterConfig.filterMode = value
        RemoteFilterConfig.enablePeerMode = value == "peer"
        viewModelScope.launch { RemoteFilterConfig.save(context) }
        filterItems = RemoteFilterConfig.getActiveFilterList()
    }

    fun setEnableDedup(
        context: Context,
        value: Boolean,
    ) {
        enableDedup = value
        RemoteFilterConfig.enableDeduplication = value
        viewModelScope.launch { RemoteFilterConfig.save(context) }
    }

    fun setEnableLockScreenOnly(
        context: Context,
        value: Boolean,
    ) {
        enableLockScreenOnly = value
        RemoteFilterConfig.enableLockScreenOnly = value
        viewModelScope.launch { RemoteFilterConfig.save(context) }
    }

    fun setEnablePackageGroupMapping(
        context: Context,
        value: Boolean,
    ) {
        enablePackageGroupMapping = value
        RemoteFilterConfig.enablePackageGroupMapping = value
        viewModelScope.launch { RemoteFilterConfig.save(context) }
    }

    fun setGroupEnabled(
        context: Context,
        newEnabled: MutableList<Boolean>,
    ) {
        allGroupEnabled = newEnabled
        val defaultSize = defaultGroupSize
        RemoteFilterConfig.defaultGroupEnabled = newEnabled.take(defaultSize).toMutableList()
        RemoteFilterConfig.customGroupEnabled = newEnabled.drop(defaultSize).toMutableList()
        viewModelScope.launch { RemoteFilterConfig.save(context) }
    }

    fun setCustomGroups(
        context: Context,
        newGroups: List<MutableList<String>>,
        newEnabled: MutableList<Boolean>,
    ) {
        val defaultSize = defaultGroupSize
        allGroups =
            RemoteFilterConfig.defaultPackageGroups.map { it.toMutableList() } +
            newGroups.drop(defaultSize).map { it.toMutableList() }
        allGroupEnabled = newEnabled
        RemoteFilterConfig.customPackageGroups = newGroups.drop(defaultSize).map { it.toMutableList() }.toMutableList()
        RemoteFilterConfig.customGroupEnabled = newEnabled.drop(defaultSize).toMutableList()
        viewModelScope.launch { RemoteFilterConfig.save(context) }
    }

    fun setEntryEnabled(
        context: Context,
        packageName: String,
        keyword: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            RemoteFilterConfig.setFilterEntryEnabled(context, packageName, keyword, enabled)
            filterItems = RemoteFilterConfig.getActiveFilterList()
            refreshEnabledKeys()
        }
    }

    fun addEntry(
        context: Context,
        packageName: String,
        keyword: String,
    ) {
        viewModelScope.launch {
            RemoteFilterConfig.addFilterEntry(context, packageName, keyword)
            filterItems = RemoteFilterConfig.getActiveFilterList()
        }
    }

    fun removeEntry(
        context: Context,
        packageName: String,
        keyword: String,
    ) {
        viewModelScope.launch {
            RemoteFilterConfig.removeFilterEntry(context, packageName, keyword)
            filterItems = RemoteFilterConfig.getActiveFilterList()
        }
    }

    fun mapToLocalPackage(
        packageName: String,
        installedPackages: Set<String>,
    ): String = RemoteFilterConfig.mapToLocalPackage(packageName, installedPackages)

    fun isDataLoaded(): Boolean = InstalledAppsRepository.isDataLoaded()

    fun loadApps(context: Context) {
        viewModelScope.launch { InstalledAppsRepository.loadApps(context) }
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
