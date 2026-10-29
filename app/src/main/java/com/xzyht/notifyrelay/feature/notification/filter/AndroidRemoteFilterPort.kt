package com.xzyht.notifyrelay.feature.notification.filter

import notifyrelay.data.filter.RemoteFilterRepository
import notifyrelay.domain.feature.notification.filter.RemoteFilterPort

/**
 * 以公共仓库 [RemoteFilterRepository] 实现 [RemoteFilterPort]。
 *
 * 仓储由应用启动时创建并传入，Android 与桌面共用同一份实现；
 * 本类只提供对端口单例的访问入口。
 */
class AndroidRemoteFilterPort(
    private val repository: RemoteFilterRepository,
) : RemoteFilterPort {
    override var filterMode: String
        get() = repository.filterMode
        set(value) {
            repository.filterMode = value
        }

    override var enableDedup: Boolean
        get() = repository.enableDedup
        set(value) {
            repository.enableDedup = value
        }

    override var enablePackageGroupMapping: Boolean
        get() = repository.enablePackageGroupMapping
        set(value) {
            repository.enablePackageGroupMapping = value
        }

    override var enableLockScreenOnly: Boolean
        get() = repository.enableLockScreenOnly
        set(value) {
            repository.enableLockScreenOnly = value
        }

    override var enablePeerMode: Boolean
        get() = repository.enablePeerMode
        set(value) {
            repository.enablePeerMode = value
        }

    override val defaultPackageGroups: List<List<String>>
        get() = repository.defaultPackageGroups

    override var defaultGroupEnabled: List<Boolean>
        get() = repository.defaultGroupEnabled
        set(value) {
            repository.defaultGroupEnabled = value
        }

    override var customPackageGroups: List<List<String>>
        get() = repository.customPackageGroups
        set(value) {
            repository.customPackageGroups = value
        }

    override var customGroupEnabled: List<Boolean>
        get() = repository.customGroupEnabled
        set(value) {
            repository.customGroupEnabled = value
        }

    override var isLoaded: Boolean
        get() = repository.isLoaded
        set(value) {
            repository.isLoaded = value
        }

    override suspend fun load() = repository.load()

    override suspend fun save() = repository.save()

    override fun activeFilterList(): List<Pair<String, String?>> = repository.activeFilterList()

    override fun isActiveEntryEnabled(
        packageName: String,
        keyword: String,
    ): Boolean = repository.isActiveEntryEnabled(packageName, keyword)

    override suspend fun setEntryEnabled(
        packageName: String,
        keyword: String,
        enabled: Boolean,
    ) = repository.setEntryEnabled(packageName, keyword, enabled)

    override suspend fun addEntry(
        packageName: String,
        keyword: String,
    ) = repository.addEntry(packageName, keyword)

    override suspend fun removeEntry(
        packageName: String,
        keyword: String,
    ) = repository.removeEntry(packageName, keyword)

    override fun mapToLocalPackage(
        packageName: String,
        installedPackages: Set<String>,
    ): String = repository.mapToLocalPackage(packageName, installedPackages)

    override fun checkFilterAccepted(
        packageName: String,
        title: String,
        text: String,
    ): Boolean = repository.checkFilterAccepted(packageName, title, text)

    /** 构建同步给 Rust 的配置 JSON。 */
    fun buildRustConfigJson(installedPackages: Set<String>): String = repository.buildRustConfigJson(installedPackages)

    /** 已启用组构成的包名等价组。 */
    fun packageGroups(): List<Set<String>> = repository.packageGroups

    /** 把当前配置同步到 Rust Core；成功返回 true。 */
    fun syncToRust(installedPackages: Set<String>): Boolean = repository.syncToRust(installedPackages)
}
