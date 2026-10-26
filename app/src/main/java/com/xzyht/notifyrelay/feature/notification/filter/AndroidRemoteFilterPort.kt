package com.xzyht.notifyrelay.feature.notification.filter

import android.content.Context
import notifyrelay.domain.feature.notification.filter.RemoteFilterPort

/** 以 [RemoteFilterConfig] 实现 [RemoteFilterPort]。 */
class AndroidRemoteFilterPort(
    private val context: Context,
) : RemoteFilterPort {
    override var filterMode: String
        get() = RemoteFilterConfig.filterMode
        set(value) {
            RemoteFilterConfig.filterMode = value
        }

    override var enableDedup: Boolean
        get() = RemoteFilterConfig.enableDeduplication
        set(value) {
            RemoteFilterConfig.enableDeduplication = value
        }

    override var enablePackageGroupMapping: Boolean
        get() = RemoteFilterConfig.enablePackageGroupMapping
        set(value) {
            RemoteFilterConfig.enablePackageGroupMapping = value
        }

    override var enableLockScreenOnly: Boolean
        get() = RemoteFilterConfig.enableLockScreenOnly
        set(value) {
            RemoteFilterConfig.enableLockScreenOnly = value
        }

    override val defaultPackageGroups: List<List<String>>
        get() = RemoteFilterConfig.defaultPackageGroups

    override var defaultGroupEnabled: List<Boolean>
        get() = RemoteFilterConfig.defaultGroupEnabled
        set(value) {
            RemoteFilterConfig.defaultGroupEnabled = value.toMutableList()
        }

    override var customPackageGroups: List<List<String>>
        get() = RemoteFilterConfig.customPackageGroups
        set(value) {
            RemoteFilterConfig.customPackageGroups = value.map { it.toMutableList() }.toMutableList()
        }

    override var customGroupEnabled: List<Boolean>
        get() = RemoteFilterConfig.customGroupEnabled
        set(value) {
            RemoteFilterConfig.customGroupEnabled = value.toMutableList()
        }

    override var isLoaded: Boolean
        get() = RemoteFilterConfig.isLoaded
        set(value) {
            RemoteFilterConfig.isLoaded = value
        }

    override suspend fun load() {
        RemoteFilterConfig.load(context)
    }

    override suspend fun save() {
        RemoteFilterConfig.save(context)
    }

    override fun activeFilterList(): List<Pair<String, String?>> = RemoteFilterConfig.getActiveFilterList()

    override fun isActiveEntryEnabled(
        packageName: String,
        keyword: String,
    ): Boolean = RemoteFilterConfig.isActiveEntryEnabled(packageName, keyword)

    override suspend fun setEntryEnabled(
        packageName: String,
        keyword: String,
        enabled: Boolean,
    ) {
        RemoteFilterConfig.setFilterEntryEnabled(context, packageName, keyword, enabled)
    }

    override suspend fun addEntry(
        packageName: String,
        keyword: String,
    ) {
        RemoteFilterConfig.addFilterEntry(context, packageName, keyword)
    }

    override suspend fun removeEntry(
        packageName: String,
        keyword: String,
    ) {
        RemoteFilterConfig.removeFilterEntry(context, packageName, keyword)
    }

    override fun mapToLocalPackage(
        packageName: String,
        installedPackages: Set<String>,
    ): String = RemoteFilterConfig.mapToLocalPackage(packageName, installedPackages)
}
