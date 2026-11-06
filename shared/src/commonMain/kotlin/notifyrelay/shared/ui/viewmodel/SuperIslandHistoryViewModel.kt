package notifyrelay.shared.ui.viewmodel

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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import notifyrelay.domain.feature.notification.history.GroupedSuperIslandHistory
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryEntry
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryPort
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryUiState

/**
 * 超级岛历史页的 ViewModel（平台无关）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SuperIslandHistoryViewModel(
    private val port: SuperIslandHistoryPort,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SuperIslandHistoryUiState())
    val uiState: StateFlow<SuperIslandHistoryUiState> = _uiState.asStateFlow()

    /** 图标缓存与预加载逻辑两个历史页共用，见 [HistoryIconCache]。 */
    private val iconCache = HistoryIconCache(viewModelScope) { packageNames -> port.appIcons(packageNames) }
    val appIconCache = iconCache.icons

    private val refreshSignal = MutableStateFlow(0L)

    /**
     * 分页流：删除或清空后重建 [Pager]，使列表立即刷新。
     *
     * [refreshSignal] 每次递增即触发重建；[historyChanged] 同样接入该入口。
     */
    val groupedPagingFlow: Flow<PagingData<GroupedSuperIslandHistory>> =
        refreshSignal
            .flatMapLatest {
                Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) { port.pagingSource() }.flow
            }.cachedIn(viewModelScope)

    init {
        observeIconUpdates(port.iconUpdates, iconCache)
        viewModelScope.launch {
            // 外部写入（入站新增、清空）同样触发列表重建
            port.historyChanged.collect { refreshPaging() }
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

    /** 批量补图标；参数与缓存去重由 [HistoryIconCache.preload] 负责。 */
    fun preloadAppIcons(packageNames: List<String>) = iconCache.preload(packageNames)

    private fun refreshPaging() {
        refreshSignal.value = refreshSignal.value + 1
    }
}
