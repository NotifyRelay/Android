package notifyrelay.shared.ui.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import notifyrelay.domain.feature.notification.history.AppIconInfo
import notifyrelay.domain.feature.notification.history.GroupedSuperIslandHistory
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryEntry
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryPort
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryUiState

/**
 * 超级岛历史页的 ViewModel（平台无关）。
 */
class SuperIslandHistoryViewModel(
    private val port: SuperIslandHistoryPort,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SuperIslandHistoryUiState())
    val uiState: StateFlow<SuperIslandHistoryUiState> = _uiState.asStateFlow()

    private val _appIconCache = MutableStateFlow<Map<String, Pair<String, ImageBitmap?>>>(emptyMap())
    val appIconCache: StateFlow<Map<String, Pair<String, ImageBitmap?>>> = _appIconCache.asStateFlow()

    private val refreshSignal = MutableStateFlow(0L)
    private val iconLoading = mutableSetOf<String>()

    val groupedPagingFlow: Flow<PagingData<GroupedSuperIslandHistory>> =
        Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) { port.pagingSource() }
            .flow
            .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            port.iconUpdates.collect { update ->
                update?.let { (packageName, _) ->
                    _appIconCache.update { it - packageName }
                    preloadAppIcons(listOf(packageName))
                }
            }
        }
    }

    fun toggleGroupExpansion(packageName: String) {
        _uiState.update {
            val expanded = it.expandedGroups.toMutableSet()
            if (!expanded.add(packageName)) expanded.remove(packageName)
            it.copy(expandedGroups = expanded)
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            port.deleteEntry(id)
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

    suspend fun loadEntryDetail(id: Long): SuperIslandHistoryEntry? = port.loadEntryDetail(id)

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
