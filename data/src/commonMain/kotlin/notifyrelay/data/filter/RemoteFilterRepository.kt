package notifyrelay.data.filter

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import notifyrelay.base.config.FilterConfigDefaults
import notifyrelay.base.log.Logger
import notifyrelay.data.Storage
import notifyrelay.data.database.entity.BlackListEntryEntity
import notifyrelay.data.database.entity.PackageGroupEntity
import notifyrelay.data.database.entity.WhiteListEntryEntity
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.domain.feature.appslist.InstalledPackagesPortRegistry
import notifyrelay.domain.feature.notification.filter.NativeFilterPort
import notifyrelay.domain.feature.notification.filter.NativeFilterPortRegistry
import notifyrelay.domain.feature.notification.filter.RemoteFilterPort

/**
 * 远端过滤配置仓库。
 *
 * 承载过滤模式、智能去重、包名等价组与黑白名单的状态与持久化；
 * Rust 过滤能力经 [NativeFilterPort] 注入，已安装包集合经 [InstalledPackagesPortRegistry] 注入。
 *
 * 名单类配置（黑白名单、包名组）存独立表，标量配置存 `app_config`。
 */
class RemoteFilterRepository(
    private val nativeFilter: NativeFilterPort? = NativeFilterPortRegistry.current,
    private val installedPackagesProvider: () -> Set<String> = {
        InstalledPackagesPortRegistry.current?.installedPackagesSync() ?: emptySet()
    },
) : RemoteFilterPort {
    private companion object {
        const val TAG = "RemoteFilterConfig"
        const val KEY_FILTER_MODE = "filter_mode"
        const val KEY_ENABLE_DEDUP = "enable_dedup"
        const val KEY_ENABLE_PEER = "enable_peer"
        const val KEY_ENABLE_PACKAGE_GROUP_MAPPING = "enable_package_group_mapping"
        const val KEY_ENABLE_LOCK_SCREEN_ONLY = "enable_lock_screen_only"

        /** 过滤模式的持久化取值。 */
        const val MODE_NONE = "none"
        const val MODE_BLACK = "black"
        const val MODE_WHITE = "white"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 保存串行化互斥锁：多个 UI 回调并发触发保存时排队执行，
     * 避免后触发者先完成、先触发者后完成覆盖回旧状态的竞态。
     */
    private val saveLock = Mutex()

    override var isLoaded: Boolean = false

    override var enablePackageGroupMapping: Boolean = true

    override val defaultPackageGroups: List<List<String>>
        get() = FilterConfigDefaults.defaultPackageGroups

    override var defaultGroupEnabled: List<Boolean> = mutableListOf(true, true, true)

    override var customPackageGroups: List<List<String>> = mutableListOf()

    override var customGroupEnabled: List<Boolean> = mutableListOf()

    /** 合并后的包名等价组（仅启用组）。 */
    val packageGroups: List<Set<String>>
        get() =
            if (!enablePackageGroupMapping) {
                emptyList()
            } else {
                defaultPackageGroups.withIndex().filter { defaultGroupEnabled.getOrNull(it.index) == true }.map { it.value.toSet() } +
                    customPackageGroups.withIndex().filter { customGroupEnabled.getOrNull(it.index) == true }.map { it.value.toSet() }
            }

    override var enableDedup: Boolean = true

    override var filterMode: String = MODE_NONE

    /** 黑名单内容：包名 与 可选文本关键词。 */
    var blackList: List<Pair<String, String?>> = emptyList()

    /** 白名单内容：包名 与 可选文本关键词。 */
    var whiteList: List<Pair<String, String?>> = emptyList()

    /** 黑名单的启用条目（序列化字符串集合，允许禁用单条规则）。 */
    var blackListEnabled: Set<String> = emptySet()

    /** 白名单的启用条目。 */
    var whiteListEnabled: Set<String> = emptySet()

    /** 对等模式（仅本机存在的应用或通用应用）。 */
    override var enablePeerMode: Boolean = false

    override var enableLockScreenOnly: Boolean = true

    override suspend fun load() {
        enablePackageGroupMapping = Storage.getBoolean(KEY_ENABLE_PACKAGE_GROUP_MAPPING, true, Storage.PrefsType.FILTER)
        filterMode = Storage.getString(KEY_FILTER_MODE, MODE_NONE, Storage.PrefsType.FILTER)
        enableDedup = Storage.getBoolean(KEY_ENABLE_DEDUP, true, Storage.PrefsType.FILTER)
        enablePeerMode = Storage.getBoolean(KEY_ENABLE_PEER, false, Storage.PrefsType.FILTER)
        enableLockScreenOnly = Storage.getBoolean(KEY_ENABLE_LOCK_SCREEN_ONLY, true, Storage.PrefsType.FILTER)
        loadFilterLists()
        loadPackageGroups()
        isLoaded = true
    }

    override suspend fun save() {
        // 并发串行化：保证保存按触发顺序完成，最终持久化结果与最新 UI 状态一致
        saveLock.withLock {
            Storage.putBoolean(KEY_ENABLE_PACKAGE_GROUP_MAPPING, enablePackageGroupMapping, Storage.PrefsType.FILTER)
            Storage.putString(KEY_FILTER_MODE, filterMode, Storage.PrefsType.FILTER)
            Storage.putBoolean(KEY_ENABLE_DEDUP, enableDedup, Storage.PrefsType.FILTER)
            Storage.putBoolean(KEY_ENABLE_PEER, enablePeerMode, Storage.PrefsType.FILTER)
            Storage.putBoolean(KEY_ENABLE_LOCK_SCREEN_ONLY, enableLockScreenOnly, Storage.PrefsType.FILTER)
            saveFilterLists()
            savePackageGroups()

            // 保存后立即同步到 Rust 侧
            if (nativeFilter?.isReady() == true) {
                if (!syncToRust(installedPackagesProvider())) {
                    Logger.w(TAG, "过滤配置同步到 Rust 失败")
                }
            }
        }
    }

    override fun activeFilterList(): List<Pair<String, String?>> =
        when (filterMode) {
            MODE_BLACK -> blackList
            MODE_WHITE -> whiteList
            else -> emptyList()
        }

    override fun isActiveEntryEnabled(
        packageName: String,
        keyword: String,
    ): Boolean {
        val serialized = serializeFilterEntry(packageName, keyword)
        return when (filterMode) {
            MODE_BLACK -> blackListEnabled.contains(serialized)
            MODE_WHITE -> whiteListEnabled.contains(serialized)
            else -> true
        }
    }

    override suspend fun setEntryEnabled(
        packageName: String,
        keyword: String,
        enabled: Boolean,
    ) {
        val serialized = serializeFilterEntry(packageName, keyword)
        when (filterMode) {
            MODE_BLACK -> blackListEnabled = if (enabled) blackListEnabled + serialized else blackListEnabled - serialized
            MODE_WHITE -> whiteListEnabled = if (enabled) whiteListEnabled + serialized else whiteListEnabled - serialized
        }
        save()
    }

    override suspend fun addEntry(
        packageName: String,
        keyword: String,
    ) {
        val kw = keyword.takeIf { it.isNotBlank() }
        when (filterMode) {
            MODE_BLACK -> {
                blackList = blackList + (packageName to kw)
                blackListEnabled = blackListEnabled + serializeFilterEntry(packageName, kw)
            }
            MODE_WHITE -> {
                whiteList = whiteList + (packageName to kw)
                whiteListEnabled = whiteListEnabled + serializeFilterEntry(packageName, kw)
            }
            else -> return
        }
        save()
    }

    override suspend fun removeEntry(
        packageName: String,
        keyword: String,
    ) {
        val serialized = serializeFilterEntry(packageName, keyword)
        when (filterMode) {
            MODE_BLACK -> {
                blackList = blackList.filterNot { it.first == packageName && (it.second ?: "") == keyword }
                blackListEnabled = blackListEnabled - serialized
            }
            MODE_WHITE -> {
                whiteList = whiteList.filterNot { it.first == packageName && (it.second ?: "") == keyword }
                whiteListEnabled = whiteListEnabled - serialized
            }
        }
        save()
    }

    /** 包名映射；Rust 未就绪或端口缺失时返回原包名（fail-open）。 */
    override fun mapToLocalPackage(
        packageName: String,
        installedPackages: Set<String>,
    ): String {
        val port = nativeFilter ?: return packageName
        if (!port.isReady()) return packageName
        return port.mapLocalPackage(packageName) ?: packageName
    }

    /** 过滤模式判定；Rust 未就绪或端口缺失时放行（fail-open）。 */
    override fun checkFilterAccepted(
        packageName: String,
        title: String,
        text: String,
    ): Boolean {
        val port = nativeFilter ?: return true
        if (!port.isReady()) return true
        return port.checkFilterMode(packageName, packageName, title, text)
    }

    /** 把当前配置同步到 Rust Core；成功返回 true。 */
    fun syncToRust(installedPackages: Set<String>): Boolean {
        val port = nativeFilter ?: return false
        return port.setFilterConfig(buildRustConfigJson(installedPackages))
    }

    /**
     * 构建 Rust `setFilterConfig` 所需的 JSON。
     *
     * 字段名、`filterMode` 数值映射与 `pkg|keyword` 序列化均为 Rust 契约，不可改动。
     */
    fun buildRustConfigJson(installedPackages: Set<String>): String {
        val packageGroupsArray = mutableListOf<JsonObject>()
        val groupEnabled = mutableMapOf<String, Boolean>()
        var groupIndex = 0

        for ((index, group) in defaultPackageGroups.withIndex()) {
            val enabled = defaultGroupEnabled.getOrNull(index) ?: true
            if (enabled) {
                packageGroupsArray.add(
                    buildJsonObject {
                        put("groupName", "group_" + groupIndex)
                        put("packages", JsonArray(group.map { JsonPrimitive(it) }))
                    },
                )
                groupEnabled["group_" + groupIndex] = true
                groupIndex++
            }
        }
        for ((index, group) in customPackageGroups.withIndex()) {
            val enabled = customGroupEnabled.getOrNull(index) ?: true
            if (enabled) {
                packageGroupsArray.add(
                    buildJsonObject {
                        put("groupName", "group_" + groupIndex)
                        put("packages", JsonArray(group.map { JsonPrimitive(it) }))
                    },
                )
                groupEnabled["group_" + groupIndex] = true
                groupIndex++
            }
        }

        val filterModeNumber =
            when (filterMode) {
                MODE_WHITE -> 1
                MODE_BLACK -> 2
                else -> 0
            }

        val activeList = activeFilterList()
        val activeEnabled =
            when (filterMode) {
                MODE_BLACK -> blackListEnabled
                MODE_WHITE -> whiteListEnabled
                else -> emptySet()
            }
        val filterListArray =
            activeList
                .filter { activeEnabled.contains(serializeFilterEntry(it.first, it.second)) }
                .map { JsonPrimitive(serializeFilterEntry(it.first, it.second)) }

        val root =
            buildJsonObject {
                put("enablePackageGroupMapping", enablePackageGroupMapping)
                put("packageGroups", JsonArray(packageGroupsArray))
                put(
                    "groupEnabled",
                    JsonObject(groupEnabled.mapValues { (_, value) -> JsonPrimitive(value) }),
                )
                put("filterMode", JsonPrimitive(filterModeNumber))
                put("filterList", JsonArray(filterListArray))
                put("enablePeerMode", enablePeerMode)
                put("installedPackages", JsonArray(installedPackages.map { JsonPrimitive(it) }))
            }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /** 条目序列化：`包名` 或 `包名|关键词`。 */
    fun serializeFilterEntry(
        packageName: String,
        keyword: String?,
    ): String = packageName + (keyword?.takeIf { it.isNotBlank() }?.let { "|$it" } ?: "")

    private suspend fun loadFilterLists() {
        val repo = DatabaseRepository.getInstance()
        val blackRows = repo.getBlackList()
        blackList = blackRows.map { it.packageName to it.keyword.takeIf { k -> k.isNotBlank() } }
        blackListEnabled =
            blackRows
                .filter { it.enabled }
                .map { serializeFilterEntry(it.packageName, it.keyword) }
                .toSet()
        val whiteRows = repo.getWhiteList()
        whiteList = whiteRows.map { it.packageName to it.keyword.takeIf { k -> k.isNotBlank() } }
        whiteListEnabled =
            whiteRows
                .filter { it.enabled }
                .map { serializeFilterEntry(it.packageName, it.keyword) }
                .toSet()
    }

    private suspend fun saveFilterLists() {
        val repo = DatabaseRepository.getInstance()
        repo.replaceBlackList(
            blackList.map {
                BlackListEntryEntity(
                    packageName = it.first,
                    keyword = it.second ?: "",
                    enabled = blackListEnabled.contains(serializeFilterEntry(it.first, it.second)),
                )
            },
        )
        repo.replaceWhiteList(
            whiteList.map {
                WhiteListEntryEntity(
                    packageName = it.first,
                    keyword = it.second ?: "",
                    enabled = whiteListEnabled.contains(serializeFilterEntry(it.first, it.second)),
                )
            },
        )
    }

    private suspend fun loadPackageGroups() {
        val repo = DatabaseRepository.getInstance()
        val groups = repo.getPackageGroups()
        val items =
            repo
                .getPackageGroupItems()
                .groupBy { it.groupId }
                .mapValues { (_, value) -> value.map { it.packageName } }
        val defaultRows = groups.filter { it.isDefault }.sortedBy { it.id }
        val defaultEnabled = MutableList(defaultPackageGroups.size) { true }
        defaultRows.forEachIndexed { index, group ->
            if (index < defaultEnabled.size) defaultEnabled[index] = group.enabled
        }
        defaultGroupEnabled = defaultEnabled
        val customRows = groups.filter { !it.isDefault }.sortedBy { it.id }
        customPackageGroups = customRows.map { (items[it.id] ?: emptyList()).toMutableList() }.toMutableList()
        customGroupEnabled = customRows.map { it.enabled }.toMutableList()
    }

    private suspend fun savePackageGroups() {
        val repo = DatabaseRepository.getInstance()
        val groups = mutableListOf<PackageGroupEntity>()
        val itemPackages = mutableListOf<List<String>>()
        defaultPackageGroups.forEachIndexed { index, packages ->
            groups.add(
                PackageGroupEntity(
                    groupName = "默认组" + (index + 1),
                    enabled = defaultGroupEnabled.getOrNull(index) ?: true,
                    isDefault = true,
                ),
            )
            itemPackages.add(packages)
        }
        customPackageGroups.forEachIndexed { index, packages ->
            groups.add(
                PackageGroupEntity(
                    groupName = "自定义组" + (index + 1),
                    enabled = customGroupEnabled.getOrNull(index) ?: true,
                    isDefault = false,
                ),
            )
            itemPackages.add(packages)
        }
        repo.replacePackageGroups(groups, itemPackages)
    }
}
