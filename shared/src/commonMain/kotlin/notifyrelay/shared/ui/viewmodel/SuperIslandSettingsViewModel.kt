package notifyrelay.shared.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import notifyrelay.domain.feature.notification.superisland.SpecInjectionMode
import notifyrelay.domain.feature.notification.superisland.SuperIslandConfigPort
import notifyrelay.shared.feature.appslist.AppIconPort
import notifyrelay.shared.feature.appslist.LocalAppsPort

/**
 * 超级岛设置页的状态与操作承载（平台无关）。
 */
class SuperIslandSettingsViewModel(
    private val configPort: SuperIslandConfigPort,
    private val appsPort: LocalAppsPort,
    private val iconPort: AppIconPort,
) : ViewModel() {
    var showSuperIsland by mutableStateOf(false)
        private set

    var floatingWindowEnabled by mutableStateOf(false)
        private set

    var notificationListEnabled by mutableStateOf(false)
        private set

    var specInjectionMode by mutableStateOf(SpecInjectionMode.NONE)
        private set

    fun load() {
        showSuperIsland = configPort.isRemoteDisplayEnabled()
        floatingWindowEnabled = configPort.isFloatingWindowEnabled()
        notificationListEnabled = configPort.isNotificationListMode()
        specInjectionMode = configPort.specInjectionMode()
    }

    fun updateShowSuperIsland(value: Boolean) {
        showSuperIsland = value
        configPort.setRemoteDisplayEnabled(value)
        if (!value) {
            viewModelScope.launch { configPort.dismissAllRemote() }
        }
    }

    fun updateFloatingWindowEnabled(value: Boolean) {
        if (configPort.setFloatingWindowEnabled(value)) {
            floatingWindowEnabled = value
            viewModelScope.launch { configPort.switchChannel() }
        }
    }

    fun updateNotificationListEnabled(value: Boolean) {
        if (configPort.setNotificationListMode(value)) {
            notificationListEnabled = value
            viewModelScope.launch { configPort.switchChannel() }
        }
    }

    fun updateSpecInjectionMode(mode: SpecInjectionMode) {
        specInjectionMode = mode
        configPort.setSpecInjectionMode(mode)
    }

    fun installedPackages(): Set<String> = appsPort.installedPackageNamesSync()

    fun loadIcon(
        packageName: String,
        onResult: (androidx.compose.ui.graphics.ImageBitmap?) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(runCatching { iconPort.localIcon(packageName) }.getOrNull())
        }
    }
}
