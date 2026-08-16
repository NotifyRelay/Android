package com.xzyht.notifyrelay.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.xzyht.notifyrelay.feature.device.model.NotificationRepository
import com.xzyht.notifyrelay.feature.notification.backend.RemoteFilterConfig
import com.xzyht.notifyrelay.servers.appslist.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import notifyrelay.data.database.repository.DatabaseRepository

class NotificationHistoryViewModel(
    private val application: Application,
    private val repository: DatabaseRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationHistoryUiState())
    val uiState: StateFlow<NotificationHistoryUiState> = _uiState.asStateFlow()

    private val _appIconCache = MutableStateFlow<Map<String, Pair<String, Bitmap?>>>(emptyMap())
    val appIconCache: StateFlow<Map<String, Pair<String, Bitmap?>>> = _appIconCache.asStateFlow()

    private val installedPackages = MutableStateFlow<Set<String>>(emptySet())
    val installedPackagesState: StateFlow<Set<String>> = installedPackages.asStateFlow()

    private val deviceFlow = MutableStateFlow("本机")
    private val refreshSignal = MutableStateFlow(0L)
    private val iconLoading = mutableSetOf<String>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val groupedPagingFlow: Flow<PagingData<GroupedNotifications>> =
        combine(
            deviceFlow,
            refreshSignal,
            installedPackages,
        ) { device, _, packages ->
            device to packages
        }.flatMapLatest { (device, packages) ->
            Pager(
                config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            ) {
                NotificationPagingSource(
                    repository = repository,
                    deviceUuid = device,
                    installedPackages = packages,
                )
            }.flow
        }.cachedIn(viewModelScope)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (!RemoteFilterConfig.isLoaded) {
                RemoteFilterConfig.load(application)
            }
            NotificationRepository.init(application)
            NotificationRepository.scanDeviceList(application)
            _uiState.update { state ->
                state.copy(deviceList = NotificationRepository.deviceList)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            loadInstalledPackages()
        }

        viewModelScope.launch {
            AppRepository.iconUpdates.collect { update ->
                update?.let { (packageName, _) ->
                    _appIconCache.update { cache ->
                        cache - packageName
                    }
                    preloadAppIcons(listOf(packageName))
                }
            }
        }
    }

    fun loadNotifications(device: String) {
        if (deviceFlow.value == device) {
            return
        }
        deviceFlow.value = device
        _uiState.update { it.copy(selectedDevice = device, isLoading = true, error = null) }

        viewModelScope.launch(Dispatchers.IO) {
            NotificationRepository.currentDevice = device
            NotificationRepository.notifyHistoryChanged(device, application)
            NotificationRepository.scanDeviceList(application)
            _uiState.update { state ->
                state.copy(deviceList = NotificationRepository.deviceList, isLoading = false)
            }
            refreshPaging()
        }
    }

    fun toggleGroupExpansion(packageName: String) {
        _uiState.update { state ->
            val expanded = state.expandedGroups.toMutableSet()
            if (!expanded.add(packageName)) {
                expanded.remove(packageName)
            }
            state.copy(expandedGroups = expanded)
        }
    }

    fun deleteNotification(key: String) {
        val device = _uiState.value.selectedDevice
        viewModelScope.launch(Dispatchers.IO) {
            NotificationRepository.currentDevice = device
            NotificationRepository.removeNotification(key, application)
            refreshPaging()
        }
    }

    fun deleteGroup(packageName: String) {
        val device = _uiState.value.selectedDevice
        viewModelScope.launch(Dispatchers.IO) {
            NotificationRepository.currentDevice = device
            NotificationRepository.removeNotificationsByPackage(packageName, application)
            _uiState.update { state ->
                state.copy(expandedGroups = state.expandedGroups - packageName)
            }
            refreshPaging()
        }
    }

    fun clearHistory() {
        val device = _uiState.value.selectedDevice
        viewModelScope.launch(Dispatchers.IO) {
            NotificationRepository.currentDevice = device
            NotificationRepository.clearDeviceHistory(device, application)
            _appIconCache.value = emptyMap()
            _uiState.update { state ->
                state.copy(expandedGroups = emptySet())
            }
            refreshPaging()
        }
    }

    fun preloadAppIcons(packageNames: List<String>) {
        val targets = packageNames.filter { it.isNotBlank() }
        if (targets.isEmpty()) return

        val toLoad =
            synchronized(iconLoading) {
                val cache = _appIconCache.value
                val loadTargets =
                    targets.filter { pkg ->
                        !iconLoading.contains(pkg) && cache[pkg] == null
                    }
                iconLoading.addAll(loadTargets)
                loadTargets
            }

        if (toLoad.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updates = mutableMapOf<String, Pair<String, Bitmap?>>()
                for (packageName in toLoad) {
                    updates[packageName] = getAppNameAndIcon(packageName)
                }
                if (updates.isNotEmpty()) {
                    _appIconCache.update { cache ->
                        cache + updates
                    }
                }
            } finally {
                synchronized(iconLoading) {
                    iconLoading.removeAll(toLoad.toSet())
                }
            }
        }
    }

    private fun refreshPaging() {
        refreshSignal.value = System.currentTimeMillis()
    }

    private suspend fun loadInstalledPackages() {
        val cached = AppRepository.getInstalledPackageNames(application)
        installedPackages.value =
            cached.ifEmpty {
                AppRepository.getInstalledPackageNamesAsync(application)
            }
    }

    private suspend fun getAppNameAndIcon(packageName: String): Pair<String, Bitmap?> {
        var name: String
        try {
            val pm = application.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            name = pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            name = packageName
        }
        val icon: Bitmap? =
            try {
                AppRepository.getAppIconWithAutoRequest(application, packageName)
            } catch (_: Exception) {
                null
            }
        return name to icon
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NotificationHistoryViewModel::class.java)) {
                val repository = DatabaseRepository.getInstance(application)
                @Suppress("UNCHECKED_CAST")
                return NotificationHistoryViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
