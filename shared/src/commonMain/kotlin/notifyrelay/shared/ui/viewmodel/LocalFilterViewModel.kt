package notifyrelay.shared.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import notifyrelay.domain.feature.notification.filter.FilterEntry
import notifyrelay.domain.feature.notification.filter.LocalFilterPort

/**
 * 本机通知过滤设置的状态与操作承载（平台无关）。
 *
 * 过滤数据经 [LocalFilterPort] 提供，本类不持有平台上下文。
 */
class LocalFilterViewModel(
    private val port: LocalFilterPort,
) : ViewModel() {
    var filterSelf by mutableStateOf(port.filterSelf)
        private set

    var filterOngoing by mutableStateOf(port.filterOngoing)
        private set

    var filterNoTitleOrText by mutableStateOf(port.filterNoTitleOrText)
        private set

    var filterImportanceNone by mutableStateOf(port.filterImportanceNone)
        private set

    var allEntries by mutableStateOf<List<FilterEntry>>(emptyList())
        private set

    var enabledEntries by mutableStateOf<Set<FilterEntry>>(emptySet())
        private set

    val builtinKeywords: Set<String> = port.builtinKeywords()
    val builtinPackages: Set<String> = port.defaultPackageFilters()

    fun load() {
        viewModelScope.launch {
            allEntries = port.entries().toList()
            enabledEntries = port.enabledEntries()
        }
    }

    fun updateFilterSelf(value: Boolean) {
        filterSelf = value
        port.filterSelf = value
    }

    fun updateFilterOngoing(value: Boolean) {
        filterOngoing = value
        port.filterOngoing = value
    }

    fun updateFilterNoTitleOrText(value: Boolean) {
        filterNoTitleOrText = value
        port.filterNoTitleOrText = value
    }

    fun updateFilterImportanceNone(value: Boolean) {
        filterImportanceNone = value
        port.filterImportanceNone = value
    }

    fun setEntryEnabled(
        entry: FilterEntry,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            port.setEntryEnabled(entry, enabled)
            enabledEntries = port.enabledEntries()
        }
    }

    fun addEntry(
        keyword: String,
        packageName: String,
    ) {
        viewModelScope.launch {
            port.addEntry(keyword, packageName)
            allEntries = port.entries().toList()
            enabledEntries = port.enabledEntries()
        }
    }

    fun removeEntry(
        keyword: String,
        packageName: String,
    ) {
        viewModelScope.launch {
            port.removeEntry(keyword, packageName)
            allEntries = port.entries().toList()
            enabledEntries = port.enabledEntries()
        }
    }
}
