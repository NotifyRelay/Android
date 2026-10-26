package com.xzyht.notifyrelay.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xzyht.notifyrelay.feature.notification.filter.BackendLocalFilter
import kotlinx.coroutines.launch

/**
 * 本机通知过滤设置的状态与操作承载。
 *
 * 界面只读取 [uiState] 并调用动作方法，不再直接访问 [BackendLocalFilter]。
 */
class LocalFilterViewModel : ViewModel() {
    var filterSelf by mutableStateOf(BackendLocalFilter.filterSelf)
        private set

    var filterOngoing by mutableStateOf(BackendLocalFilter.filterOngoing)
        private set

    var filterNoTitleOrText by mutableStateOf(BackendLocalFilter.filterNoTitleOrText)
        private set

    var filterImportanceNone by mutableStateOf(BackendLocalFilter.filterImportanceNone)
        private set

    var allEntries by mutableStateOf<List<BackendLocalFilter.FilterEntry>>(emptyList())
        private set

    var enabledEntries by mutableStateOf<Set<BackendLocalFilter.FilterEntry>>(emptySet())
        private set

    val builtinKeywords: Set<String> = BackendLocalFilter.getBuiltinKeywords()
    val builtinPackages: Set<String> = BackendLocalFilter.getDefaultPackageFilters()

    fun load(context: Context) {
        viewModelScope.launch {
            allEntries = BackendLocalFilter.getFilterEntries(context).toList()
            enabledEntries = BackendLocalFilter.getEnabledFilterEntries(context)
        }
    }

    fun updateFilterSelf(value: Boolean) {
        filterSelf = value
        BackendLocalFilter.filterSelf = value
    }

    fun updateFilterOngoing(value: Boolean) {
        filterOngoing = value
        BackendLocalFilter.filterOngoing = value
    }

    fun updateFilterNoTitleOrText(value: Boolean) {
        filterNoTitleOrText = value
        BackendLocalFilter.filterNoTitleOrText = value
    }

    fun updateFilterImportanceNone(value: Boolean) {
        filterImportanceNone = value
        BackendLocalFilter.filterImportanceNone = value
    }

    fun setEntryEnabled(
        context: Context,
        keyword: String,
        packageName: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            BackendLocalFilter.setFilterEntryEnabled(
                context,
                BackendLocalFilter.FilterEntry(keyword, packageName),
                enabled,
            )
            enabledEntries = BackendLocalFilter.getEnabledFilterEntries(context)
        }
    }

    fun addEntry(
        context: Context,
        keyword: String,
        packageName: String,
    ) {
        viewModelScope.launch {
            BackendLocalFilter.addFilterEntry(context, keyword, packageName)
            allEntries = BackendLocalFilter.getFilterEntries(context).toList()
            enabledEntries = BackendLocalFilter.getEnabledFilterEntries(context)
        }
    }

    fun removeEntry(
        context: Context,
        keyword: String,
        packageName: String,
    ) {
        viewModelScope.launch {
            BackendLocalFilter.removeFilterEntry(context, keyword, packageName)
            allEntries = BackendLocalFilter.getFilterEntries(context).toList()
            enabledEntries = BackendLocalFilter.getEnabledFilterEntries(context)
        }
    }
}
