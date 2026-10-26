package notifyrelay.shared.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import notifyrelay.domain.feature.notification.filter.RemoteFilterPort
import notifyrelay.shared.feature.appslist.AppIconPort
import notifyrelay.shared.feature.appslist.LocalAppsPort

/**
 * 远端过滤设置的状态与操作承载（平台无关）。
 */
class RemoteFilterViewModel(
    private val port: RemoteFilterPort,
    private val appsPort: LocalAppsPort,
    private val iconPort: AppIconPort,
) : ViewModel() {
    var filterMode by mutableStateOf(port.filterMode)
        private set

    var enableDedup by mutableStateOf(port.enableDedup)
        private set

    var enablePackageGroupMapping by mutableStateOf(port.enablePackageGroupMapping)
        private set

    var enableLockScreenOnly by mutableStateOf(port.enableLockScreenOnly)
        private set

    var allGroups by mutableStateOf<List<List<String>>>(emptyList())
        private set

    var allGroupEnabled by mutableStateOf<List<Boolean>>(emptyList())
        private set

    var filterItems by mutableStateOf<List<Pair<String, String?>>>(emptyList())
        private set

    var enabledKeys by mutableStateOf<Set<String>>(emptySet())
        private set

    val installedPkgs get() = appsPort.apps

    val defaultGroupSize: Int get() = port.defaultPackageGroups.size

    fun isConfigLoaded(): Boolean = port.isLoaded

    fun ensureLoaded() {
        if (!port.isLoaded) {
            viewModelScope.launch {
                port.load()
                port.isLoaded = true
                syncFromConfig()
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            if (!port.isLoaded) {
                port.load()
                port.isLoaded = true
            }
            syncFromConfig()
        }
    }

    /** 从端口同步全部状态。 */
    fun syncFromConfig() {
        filterMode = port.filterMode
        enableDedup = port.enableDedup
        enablePackageGroupMapping = port.enablePackageGroupMapping
        enableLockScreenOnly = port.enableLockScreenOnly
        allGroups = port.defaultPackageGroups + port.customPackageGroups
        allGroupEnabled = port.defaultGroupEnabled + port.customGroupEnabled
        filterItems = port.activeFilterList()
        refreshEnabledKeys()
    }

    private fun refreshEnabledKeys() {
        enabledKeys =
            filterItems
                .filter { (pkg, kw) -> port.isActiveEntryEnabled(pkg, kw ?: "") }
                .map { (pkg, kw) -> pkg + "|" + (kw ?: "") }
                .toSet()
    }

    fun updateFilterMode(value: String) {
        filterMode = value
        port.filterMode = value
        viewModelScope.launch {
            port.save()
            filterItems = port.activeFilterList()
            refreshEnabledKeys()
        }
    }

    fun updateEnableDedup(value: Boolean) {
        enableDedup = value
        port.enableDedup = value
        viewModelScope.launch { port.save() }
    }

    fun updateEnableLockScreenOnly(value: Boolean) {
        enableLockScreenOnly = value
        port.enableLockScreenOnly = value
        viewModelScope.launch { port.save() }
    }

    fun updateEnablePackageGroupMapping(value: Boolean) {
        enablePackageGroupMapping = value
        port.enablePackageGroupMapping = value
        viewModelScope.launch { port.save() }
    }

    fun updateGroupEnabled(newEnabled: List<Boolean>) {
        allGroupEnabled = newEnabled
        val defaultSize = defaultGroupSize
        port.defaultGroupEnabled = newEnabled.take(defaultSize)
        port.customGroupEnabled = newEnabled.drop(defaultSize)
        viewModelScope.launch { port.save() }
    }

    fun updateCustomGroups(
        newGroups: List<List<String>>,
        newEnabled: List<Boolean>,
    ) {
        val defaultSize = defaultGroupSize
        allGroups = port.defaultPackageGroups + newGroups.drop(defaultSize)
        allGroupEnabled = newEnabled
        port.customPackageGroups = newGroups.drop(defaultSize)
        port.customGroupEnabled = newEnabled.drop(defaultSize)
        viewModelScope.launch { port.save() }
    }

    fun updateEntryEnabled(
        packageName: String,
        keyword: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            port.setEntryEnabled(packageName, keyword, enabled)
            filterItems = port.activeFilterList()
            refreshEnabledKeys()
        }
    }

    fun addEntry(
        packageName: String,
        keyword: String,
    ) {
        viewModelScope.launch {
            port.addEntry(packageName, keyword)
            filterItems = port.activeFilterList()
        }
    }

    fun removeEntry(
        packageName: String,
        keyword: String,
    ) {
        viewModelScope.launch {
            port.removeEntry(packageName, keyword)
            filterItems = port.activeFilterList()
        }
    }

    fun mapToLocalPackage(
        packageName: String,
        installedPackages: Set<String>,
    ): String = port.mapToLocalPackage(packageName, installedPackages)

    fun isDataLoaded(): Boolean = appsPort.isDataLoaded()

    fun loadApps() {
        viewModelScope.launch { appsPort.load() }
    }

    fun loadIcon(
        packageName: String,
        onResult: (androidx.compose.ui.graphics.ImageBitmap?) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(runCatching { iconPort.localIcon(packageName) }.getOrNull())
        }
    }
}
