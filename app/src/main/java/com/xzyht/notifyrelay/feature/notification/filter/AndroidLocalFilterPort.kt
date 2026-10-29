package com.xzyht.notifyrelay.feature.notification.filter

import notifyrelay.data.filter.LocalFilterRepository
import notifyrelay.domain.feature.notification.filter.FilterEntry
import notifyrelay.domain.feature.notification.filter.LocalFilterPort

/** 以公共仓储 [LocalFilterRepository] 实现 [LocalFilterPort]。 */
class AndroidLocalFilterPort : LocalFilterPort {
    override var filterSelf: Boolean
        get() = LocalFilterRepository.filterSelf
        set(value) {
            LocalFilterRepository.filterSelf = value
        }

    override var filterOngoing: Boolean
        get() = LocalFilterRepository.filterOngoing
        set(value) {
            LocalFilterRepository.filterOngoing = value
        }

    override var filterNoTitleOrText: Boolean
        get() = LocalFilterRepository.filterNoTitleOrText
        set(value) {
            LocalFilterRepository.filterNoTitleOrText = value
        }

    override var filterImportanceNone: Boolean
        get() = LocalFilterRepository.filterImportanceNone
        set(value) {
            LocalFilterRepository.filterImportanceNone = value
        }

    override fun builtinKeywords(): Set<String> = LocalFilterRepository.getBuiltinKeywords()

    override fun defaultPackageFilters(): Set<String> = LocalFilterRepository.getDefaultPackageFilters()

    override suspend fun entries(): Set<FilterEntry> = LocalFilterRepository.getFilterEntries()

    override suspend fun enabledEntries(): Set<FilterEntry> = LocalFilterRepository.getEnabledFilterEntries()

    override suspend fun setEntryEnabled(
        entry: FilterEntry,
        enabled: Boolean,
    ) = LocalFilterRepository.setFilterEntryEnabled(entry, enabled)

    override suspend fun addEntry(
        keyword: String,
        packageName: String,
    ) = LocalFilterRepository.addFilterEntry(keyword, packageName)

    override suspend fun removeEntry(
        keyword: String,
        packageName: String,
    ) = LocalFilterRepository.removeFilterEntry(keyword, packageName)
}
