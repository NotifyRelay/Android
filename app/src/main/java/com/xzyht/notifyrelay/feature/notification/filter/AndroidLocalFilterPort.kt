package com.xzyht.notifyrelay.feature.notification.filter

import android.content.Context
import notifyrelay.domain.feature.notification.filter.FilterEntry
import notifyrelay.domain.feature.notification.filter.LocalFilterPort

/** 以 [BackendLocalFilter] 实现 [LocalFilterPort]。 */
class AndroidLocalFilterPort(
    private val context: Context,
) : LocalFilterPort {
    override var filterSelf: Boolean
        get() = BackendLocalFilter.filterSelf
        set(value) {
            BackendLocalFilter.filterSelf = value
        }

    override var filterOngoing: Boolean
        get() = BackendLocalFilter.filterOngoing
        set(value) {
            BackendLocalFilter.filterOngoing = value
        }

    override var filterNoTitleOrText: Boolean
        get() = BackendLocalFilter.filterNoTitleOrText
        set(value) {
            BackendLocalFilter.filterNoTitleOrText = value
        }

    override var filterImportanceNone: Boolean
        get() = BackendLocalFilter.filterImportanceNone
        set(value) {
            BackendLocalFilter.filterImportanceNone = value
        }

    override fun builtinKeywords(): Set<String> = BackendLocalFilter.getBuiltinKeywords()

    override fun defaultPackageFilters(): Set<String> = BackendLocalFilter.getDefaultPackageFilters()

    override suspend fun entries(): Set<FilterEntry> = BackendLocalFilter.getFilterEntries(context).map { FilterEntry(it.keyword, it.packageName) }.toSet()

    override suspend fun enabledEntries(): Set<FilterEntry> = BackendLocalFilter.getEnabledFilterEntries(context).map { FilterEntry(it.keyword, it.packageName) }.toSet()

    override suspend fun setEntryEnabled(
        entry: FilterEntry,
        enabled: Boolean,
    ) {
        BackendLocalFilter.setFilterEntryEnabled(
            context,
            FilterEntry(entry.keyword, entry.packageName),
            enabled,
        )
    }

    override suspend fun addEntry(
        keyword: String,
        packageName: String,
    ) {
        BackendLocalFilter.addFilterEntry(context, keyword, packageName)
    }

    override suspend fun removeEntry(
        keyword: String,
        packageName: String,
    ) {
        BackendLocalFilter.removeFilterEntry(context, keyword, packageName)
    }
}
