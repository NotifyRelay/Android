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
import com.xzyht.notifyrelay.feature.appslist.AppRepository
import com.xzyht.notifyrelay.feature.notification.superisland.history.SuperIslandHistoryStore
import com.xzyht.notifyrelay.feature.notification.superisland.history.SuperIslandHistoryStoreEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import notifyrelay.data.database.repository.DatabaseRepository

class SuperIslandHistoryViewModel(
    private val application: Application,
    private val repository: DatabaseRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SuperIslandHistoryUiState())
    val uiState: StateFlow<SuperIslandHistoryUiState> = _uiState.asStateFlow()

    private val _appIconCache = MutableStateFlow<Map<String, Pair<String, Bitmap?>>>(emptyMap())

    private val refreshSignal = MutableStateFlow(0L)
    private val iconLoading = mutableSetOf<String>()

    /** 图标批量加载共用实现；`iconUpdates` 订阅仍留在本类的 `init`（失效路径不迁移）。 */
    private val appIconPreloader =
        AppIconPreloader(
            application = application,
            scope = viewModelScope,
            cache = _appIconCache,
            iconLoading = iconLoading,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val groupedPagingFlow: Flow<PagingData<GroupedSuperIslandHistory>> =
        refreshSignal
            .flatMapLatest {
                Pager(
                    config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                ) {
                    SuperIslandPagingSource(repository, application)
                }.flow
            }.cachedIn(viewModelScope)

    init {
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

    fun toggleGroupExpansion(packageName: String) {
        _uiState.update { state ->
            val expanded = state.expandedGroups.toMutableSet()
            if (!expanded.add(packageName)) {
                expanded.remove(packageName)
            }
            state.copy(expandedGroups = expanded)
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSuperIslandHistoryById(id)
            refreshPaging()
        }
    }

    fun deleteGroup(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val actualPackageName = if (packageName == "(未知应用)") null else packageName
            repository.deleteSuperIslandHistoryByPackage(actualPackageName)
            _uiState.update { state ->
                state.copy(expandedGroups = state.expandedGroups - packageName)
            }
            refreshPaging()
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearSuperIslandHistory()
            _appIconCache.value = emptyMap()
            _uiState.update { state ->
                state.copy(expandedGroups = emptySet())
            }
            refreshPaging()
        }
    }

    suspend fun loadEntryDetail(id: Long): SuperIslandHistoryStoreEntry? = SuperIslandHistoryStore.loadEntryDetail(application, id)

    fun preloadAppIcons(packageNames: List<String>) {
        appIconPreloader.preload(packageNames, excludeUnknownApp = true)
    }

    private fun refreshPaging() {
        refreshSignal.value = System.currentTimeMillis()
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SuperIslandHistoryViewModel::class.java)) {
                val repository = DatabaseRepository.getInstance(application)
                @Suppress("UNCHECKED_CAST")
                return SuperIslandHistoryViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
