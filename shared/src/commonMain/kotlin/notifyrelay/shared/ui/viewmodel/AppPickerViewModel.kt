package notifyrelay.shared.ui.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import notifyrelay.domain.feature.appslist.model.LocalAppInfo
import notifyrelay.shared.feature.appslist.AppIconPort
import notifyrelay.shared.feature.appslist.LocalAppsPort

/**
 * 应用选择弹窗的状态与查询承载（平台无关）。
 */
class AppPickerViewModel(
    private val appsPort: LocalAppsPort,
    private val iconPort: AppIconPort,
) : ViewModel() {
    val isLoading: StateFlow<Boolean> get() = appsPort.isLoading

    val apps: StateFlow<List<LocalAppInfo>> get() = appsPort.apps

    val iconUpdateKey get() = iconPort.iconUpdates

    fun filteredApps(
        query: String,
        showSystemApps: Boolean,
    ): List<LocalAppInfo> = appsPort.filtered(query, showSystemApps)

    fun isDataLoaded(): Boolean = appsPort.isDataLoaded()

    fun loadApps() {
        viewModelScope.launch { appsPort.load() }
    }

    fun loadIcon(
        packageName: String,
        onResult: (ImageBitmap?) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(runCatching { iconPort.localIcon(packageName) }.getOrNull())
        }
    }
}
