package com.xzyht.notifyrelay.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xzyht.notifyrelay.feature.appslist.AppRepository
import com.xzyht.notifyrelay.feature.appslist.model.RemoteAppInfo
import com.xzyht.notifyrelay.feature.appslist.sync.AppListSyncManager
import com.xzyht.notifyrelay.feature.device.model.DeviceInfo
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
import io.github.miuzarte.scrcpyforandroid.pages.ShortcutLaunchActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import notifyrelay.base.util.Logger

/**
 * 远程设备应用列表的 ViewModel。
 *
 * 状态机与搜索流程统一由 [AppsStateMachine] 承担，本类只提供远程数据源实现与设备侧刷新流程。
 */
class RemoteAppsViewModel : ViewModel() {
    private var currentDeviceUuid: String? = null
    private var iconUpdatesJob: Job? = null

    private val appsMachine =
        AppsStateMachine<RemoteAppInfo> { context ->
            val deviceUuid = currentDeviceUuid ?: return@AppsStateMachine emptyList()
            AppRepository.loadPinnedApps(context, deviceUuid)
            AppRepository.getRemoteAppsList(context, deviceUuid)
        }

    val state: StateFlow<AppsState<RemoteAppInfo>> = appsMachine.state

    fun loadApps(
        context: Context,
        deviceUuid: String,
    ) {
        currentDeviceUuid = deviceUuid
        viewModelScope.launch { appsMachine.load(context) }

        observeIconUpdates(context, deviceUuid)
    }

    private fun observeIconUpdates(
        context: Context,
        deviceUuid: String,
    ) {
        iconUpdatesJob?.cancel()
        iconUpdatesJob =
            viewModelScope.launch {
                AppRepository.iconUpdates.collect { update ->
                    if (update != null) {
                        val (packageName, _) = update
                        refreshSingleAppIcon(context, deviceUuid, packageName)
                    }
                }
            }
    }

    private suspend fun refreshSingleAppIcon(
        context: Context,
        deviceUuid: String,
        packageName: String,
    ) {
        try {
            val updatedApps =
                appsMachine.state.value.apps.map { app ->
                    if (app.packageName == packageName) {
                        val updatedApp =
                            AppRepository
                                .getRemoteAppsList(context, deviceUuid)
                                .find { it.packageName == packageName }
                        updatedApp ?: app
                    } else {
                        app
                    }
                }
            appsMachine.update { it.copy(apps = updatedApps) }
        } catch (e: Exception) {
            Logger.w("RemoteAppsViewModel", "刷新单个应用图标失败: $packageName", e)
        }
    }

    fun refreshApps(context: Context) {
        val deviceUuid = currentDeviceUuid ?: return
        viewModelScope.launch {
            appsMachine.update { it.copy(isLoading = true, error = null) }
            try {
                val deviceManager = DeviceConnectionManagerSingleton.getDeviceManager(context)
                val deviceInfo = findDeviceInfo(deviceManager, deviceUuid)

                if (deviceInfo != null) {
                    Logger.d("RemoteAppsViewModel", "请求远程应用列表: ${deviceInfo.displayName}")
                    AppListSyncManager.requestAppListFromDevice(
                        context,
                        deviceManager,
                        deviceInfo,
                    )
                } else {
                    Logger.w("RemoteAppsViewModel", "未找到设备信息: $deviceUuid")
                    appsMachine.update { it.copy(isLoading = false, error = "设备未连接") }
                    return@launch
                }

                delay(2000)

                appsMachine.load(context)
            } catch (e: Exception) {
                Logger.e("RemoteAppsViewModel", "刷新应用列表失败", e)
                appsMachine.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun findDeviceInfo(
        deviceManager: DeviceConnectionManager,
        deviceUuid: String,
    ): DeviceInfo? {
        val onlineDevices = deviceManager.getAuthenticatedOnlineDevices()
        return onlineDevices.find { it.uuid == deviceUuid }
    }

    fun searchApps(query: String) = appsMachine.searchApps(query)

    fun pinApp(
        context: Context,
        packageName: String,
    ) {
        val deviceUuid = currentDeviceUuid ?: return
        viewModelScope.launch {
            AppRepository.pinApp(context, deviceUuid, packageName)
            updatePinnedState(deviceUuid)
        }
    }

    fun unpinApp(
        context: Context,
        packageName: String,
    ) {
        val deviceUuid = currentDeviceUuid ?: return
        viewModelScope.launch {
            AppRepository.unpinApp(context, deviceUuid, packageName)
            updatePinnedState(deviceUuid)
        }
    }

    private fun updatePinnedState(deviceUuid: String) {
        val pinnedSet = AppRepository.pinnedApps.value[deviceUuid] ?: emptySet()
        val updatedApps =
            appsMachine.state.value.apps.map { app ->
                app.copy(isPinned = pinnedSet.contains(app.packageName))
            }
        appsMachine.update { it.copy(apps = updatedApps) }
    }

    fun openApp(
        context: Context,
        app: RemoteAppInfo,
        deviceIp: String,
        useScrcpyStartApp: Boolean = false,
    ) {
        ShortcutLaunchActivity.startFullscreenControl(
            context = context,
            ip = deviceIp,
            port = 5555,
            name = app.appName,
            startApp = app.packageName,
            useScrcpyStartApp = useScrcpyStartApp,
        )
    }
}
