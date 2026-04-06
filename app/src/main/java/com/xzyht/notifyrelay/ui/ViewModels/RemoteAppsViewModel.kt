package com.xzyht.notifyrelay.ui.ViewModels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import com.xzyht.notifyrelay.servers.appslist.AppRepository
import com.xzyht.notifyrelay.servers.appslist.RemoteAppInfo
import com.xzyht.notifyrelay.servers.appslist.RemoteAppsState
import com.xzyht.notifyrelay.sync.AppListSyncManager
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notifyrelay.base.util.Logger

class RemoteAppsViewModel : ViewModel() {
    private val _state = MutableStateFlow(RemoteAppsState())
    val state: StateFlow<RemoteAppsState> = _state.asStateFlow()

    private var currentDeviceUuid: String? = null

    fun loadApps(context: Context, deviceUuid: String) {
        currentDeviceUuid = deviceUuid
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                AppRepository.loadPinnedApps(context)
                val apps = AppRepository.getRemoteAppsList(context, deviceUuid)
                val pinnedApps = apps.filter { it.isPinned }
                _state.update { 
                    it.copy(
                        apps = apps,
                        pinnedApps = pinnedApps,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
        
        observeIconUpdates(context, deviceUuid)
    }

    private fun observeIconUpdates(context: Context, deviceUuid: String) {
        viewModelScope.launch {
            AppRepository.iconUpdates.collect { update ->
                if (update != null) {
                    val (packageName, _) = update
                    refreshSingleAppIcon(context, deviceUuid, packageName)
                }
            }
        }
    }

    private suspend fun refreshSingleAppIcon(context: Context, deviceUuid: String, packageName: String) {
        try {
            val updatedApps = _state.value.apps.map { app ->
                if (app.packageName == packageName) {
                    val updatedApp = AppRepository.getRemoteAppsList(context, deviceUuid)
                        .find { it.packageName == packageName }
                    updatedApp ?: app
                } else {
                    app
                }
            }
            val pinnedApps = updatedApps.filter { it.isPinned }
            _state.update { 
                it.copy(
                    apps = updatedApps,
                    pinnedApps = pinnedApps
                )
            }
        } catch (e: Exception) {
            Logger.w("RemoteAppsViewModel", "刷新单个应用图标失败: $packageName", e)
        }
    }

    fun refreshApps(context: Context) {
        val deviceUuid = currentDeviceUuid ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val deviceManager = DeviceConnectionManager.getInstance(context)
                val deviceInfo = findDeviceInfo(deviceManager, deviceUuid)
                
                if (deviceInfo != null) {
                    Logger.d("RemoteAppsViewModel", "请求远程应用列表: ${deviceInfo.displayName}")
                    AppListSyncManager.requestAppListFromDevice(
                        context,
                        deviceManager,
                        deviceInfo
                    )
                } else {
                    Logger.w("RemoteAppsViewModel", "未找到设备信息: $deviceUuid")
                    _state.update { it.copy(isLoading = false, error = "设备未连接") }
                    return@launch
                }
                
                delay(2000)
                
                AppRepository.loadPinnedApps(context)
                val apps = AppRepository.getRemoteAppsList(context, deviceUuid)
                val pinnedApps = apps.filter { it.isPinned }
                _state.update { 
                    it.copy(
                        apps = apps,
                        pinnedApps = pinnedApps,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Logger.e("RemoteAppsViewModel", "刷新应用列表失败", e)
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun findDeviceInfo(deviceManager: DeviceConnectionManager, deviceUuid: String): DeviceInfo? {
        val onlineDevices = deviceManager.getAuthenticatedOnlineDevices()
        return onlineDevices.find { it.uuid == deviceUuid }
    }

    fun searchApps(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun pinApp(context: Context, packageName: String) {
        viewModelScope.launch {
            AppRepository.pinApp(context, packageName)
            updatePinnedState()
        }
    }

    fun unpinApp(context: Context, packageName: String) {
        viewModelScope.launch {
            AppRepository.unpinApp(context, packageName)
            updatePinnedState()
        }
    }

    private fun updatePinnedState() {
        val pinnedSet = AppRepository.pinnedApps.value
        val updatedApps = _state.value.apps.map { app ->
            app.copy(isPinned = pinnedSet.contains(app.packageName))
        }
        val pinnedApps = updatedApps.filter { it.isPinned }
        _state.update { it.copy(apps = updatedApps, pinnedApps = pinnedApps) }
    }

    fun openApp(context: Context, app: RemoteAppInfo, deviceIp: String) {
        viewModelScope.launch {
            try {
                val updatedApps = _state.value.apps.map { 
                    if (it.packageName == app.packageName) it.copy(isLoading = true) else it 
                }
                _state.update { it.copy(apps = updatedApps) }

                withContext(Dispatchers.IO) {
                    val nativeCore = NativeCoreFacade.get(context)
                    val port = 5555
                    
                    if (!nativeCore.adbIsConnected()) {
                        nativeCore.adbConnect(deviceIp, port)
                    }

                    val request = NativeCoreFacade.defaultStartRequest(
                        customServerUri = null,
                        maxSize = 1920,
                        videoBitRate = 8_000_000,
                        remotePath = "/data/local/tmp/scrcpy-server.jar",
                        newDisplay = "1920x1080",
                        startApp = app.packageName,
                        windowTitle = app.appName,
                    )
                    nativeCore.scrcpyStart(request)
                }

                val resetApps = _state.value.apps.map { 
                    if (it.packageName == app.packageName) it.copy(isLoading = false) else it 
                }
                _state.update { it.copy(apps = resetApps) }
            } catch (e: Exception) {
                val resetApps = _state.value.apps.map { 
                    if (it.packageName == app.packageName) it.copy(isLoading = false) else it 
                }
                _state.update { it.copy(apps = resetApps, error = e.message) }
            }
        }
    }
}
