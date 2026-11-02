package notifyrelay.shared.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import notifyrelay.base.log.Logger
import notifyrelay.domain.feature.appslist.RemoteAppsPort
import notifyrelay.domain.feature.appslist.model.RemoteAppInfo

/**
 * 远端应用列表的 ViewModel（平台无关）。
 */
class RemoteAppsViewModel(
    private val port: RemoteAppsPort,
) : ViewModel() {
    private var currentDeviceUuid: String? = null
    private var loadJob: Job? = null
    private var iconUpdatesJob: Job? = null

    private val appsMachine =
        AppsStateMachine<RemoteAppInfo, String> { deviceUuid ->
            port.loadPinned(deviceUuid)
            port.remoteApps(deviceUuid)
        }

    val state: StateFlow<AppsState<RemoteAppInfo>> = appsMachine.state

    fun loadApps(deviceUuid: String) {
        currentDeviceUuid = deviceUuid
        loadJob?.cancel()
        loadJob = viewModelScope.launch { appsMachine.load(deviceUuid) }
        observeIconUpdates(deviceUuid)
    }

    private fun observeIconUpdates(deviceUuid: String) {
        iconUpdatesJob?.cancel()
        iconUpdatesJob =
            viewModelScope.launch {
                port.iconUpdates.collect { update ->
                    if (update != null) {
                        val (packageName, _) = update
                        refreshSingleAppIcon(deviceUuid, packageName)
                    }
                }
            }
    }

    private suspend fun refreshSingleAppIcon(
        deviceUuid: String,
        packageName: String,
    ) {
        try {
            val refreshed = port.remoteApps(deviceUuid).find { it.packageName == packageName }
            if (refreshed == null) return
            val updatedApps =
                appsMachine.state.value.apps
                    .map { if (it.packageName == packageName) refreshed else it }
            appsMachine.update { it.copy(apps = updatedApps) }
        } catch (e: Exception) {
            Logger.w("RemoteAppsViewModel", "刷新单个应用图标失败: " + packageName, e)
        }
    }

    fun refreshApps() {
        val deviceUuid = currentDeviceUuid ?: return
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                appsMachine.update { it.copy(isLoading = true, error = null) }
                try {
                    if (port.findOnlineDevice(deviceUuid) == null) {
                        Logger.w("RemoteAppsViewModel", "未找到设备信息: " + deviceUuid)
                        appsMachine.update { it.copy(isLoading = false, error = "设备未连接") }
                        return@launch
                    }
                    port.requestAppList(deviceUuid)
                    delay(2000)
                    appsMachine.load(deviceUuid) { e -> Logger.e("RemoteAppsViewModel", "刷新应用列表失败", e) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e("RemoteAppsViewModel", "刷新应用列表失败", e)
                    appsMachine.update { it.copy(isLoading = false, error = e.message) }
                }
            }
    }

    fun searchApps(query: String) = appsMachine.searchApps(query)

    fun pinApp(packageName: String) {
        val deviceUuid = currentDeviceUuid ?: return
        viewModelScope.launch {
            port.pin(deviceUuid, packageName)
            updatePinnedState(deviceUuid)
        }
    }

    fun unpinApp(packageName: String) {
        val deviceUuid = currentDeviceUuid ?: return
        viewModelScope.launch {
            port.unpin(deviceUuid, packageName)
            updatePinnedState(deviceUuid)
        }
    }

    private fun updatePinnedState(deviceUuid: String) {
        val pinnedSet = port.pinnedApps.value[deviceUuid] ?: emptySet()
        val updatedApps =
            appsMachine.state.value.apps.map { app ->
                app.copy(isPinned = pinnedSet.contains(app.packageName))
            }
        appsMachine.update { it.copy(apps = updatedApps) }
    }

    /** 在当前设备上启动应用；设备取本列表的 [currentDeviceUuid]。 */
    fun openApp(
        app: RemoteAppInfo,
        useScrcpyStartApp: Boolean = false,
    ) {
        val deviceUuid = currentDeviceUuid ?: return
        port.launchApp(app, deviceUuid, useScrcpyStartApp)
    }
}
