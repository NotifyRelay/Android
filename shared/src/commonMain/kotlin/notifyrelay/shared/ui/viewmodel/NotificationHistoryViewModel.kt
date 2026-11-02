package notifyrelay.shared.ui.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import notifyrelay.domain.feature.notification.history.AppIconInfo
import notifyrelay.domain.feature.notification.history.GroupedNotifications
import notifyrelay.domain.feature.notification.history.NotificationHistoryPort
import notifyrelay.domain.feature.notification.history.NotificationHistoryUiState

/**
 * 通知历史页的 ViewModel（平台无关）。
 */
class NotificationHistoryViewModel(
    private val port: NotificationHistoryPort,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationHistoryUiState())
    val uiState: StateFlow<NotificationHistoryUiState> = _uiState.asStateFlow()

    private val _appIconCache = MutableStateFlow<Map<String, Pair<String, ImageBitmap?>>>(emptyMap())
    val appIconCache: StateFlow<Map<String, Pair<String, ImageBitmap?>>> = _appIconCache.asStateFlow()

    private val installedPackagesFlow = MutableStateFlow<Set<String>>(emptySet())
    val installedPackagesState: StateFlow<Set<String>> = installedPackagesFlow.asStateFlow()

    private val deviceFlow = MutableStateFlow("本机")
    private val refreshSignal = MutableStateFlow(0L)
    private val iconLoading = mutableSetOf<String>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val groupedPagingFlow: Flow<PagingData<GroupedNotifications>> =
        combine(deviceFlow, refreshSignal, installedPackagesFlow) { device, _, packages -> device to packages }
            .flatMapLatest { (device, packages) ->
                Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                    port.pagingSource(device, packages)
                }.flow
            }.cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(deviceList = port.deviceList()) }
            installedPackagesFlow.value = port.installedPackages()
        }
        viewModelScope.launch {
            port.iconUpdates.collect { update ->
                update?.let { (packageName, _) ->
                    _appIconCache.update { it - packageName }
                    preloadAppIcons(listOf(packageName))
                }
            }
        }
        viewModelScope.launch {
            port.historyChanged.collect { refreshSignal.value = it }
        }
    }

    fun loadNotifications(device: String) {
        if (deviceFlow.value == device) return
        deviceFlow.value = device
        _uiState.update { it.copy(selectedDevice = device, isLoading = true, error = null) }
        viewModelScope.launch {
            port.selectDevice(device)
            _uiState.update { it.copy(deviceList = port.deviceList(), isLoading = false) }
            refreshPaging()
        }
    }

    fun toggleGroupExpansion(packageName: String) {
        _uiState.update {
            val expanded = it.expandedGroups.toMutableSet()
            if (!expanded.add(packageName)) expanded.remove(packageName)
            it.copy(expandedGroups = expanded)
        }
    }

    fun deleteNotification(key: String) {
        viewModelScope.launch {
            port.deleteNotification(key)
            refreshPaging()
        }
    }

    fun deleteGroup(packageName: String) {
        viewModelScope.launch {
            port.deleteGroup(packageName)
            refreshPaging()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            port.clearHistory()
            refreshPaging()
        }
    }

    fun preloadAppIcons(packageNames: List<String>) {
        val missing = packageNames.filter { it.isNotBlank() && !_appIconCache.value.containsKey(it) && iconLoading.add(it) }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            try {
                val icons: Map<String, AppIconInfo> = port.appIcons(missing)
                _appIconCache.update { cache ->
                    cache + icons.mapValues { (_, info) -> info.appName to info.icon }
                }
            } finally {
                iconLoading.removeAll(missing.toSet())
            }
        }
    }

    private fun refreshPaging() {
        refreshSignal.value = refreshSignal.value + 1
    }
}
