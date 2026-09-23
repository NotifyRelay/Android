package com.xzyht.notifyrelay.feature.notification.filter

import android.content.Context
import com.sun.jna.Pointer
import com.xzyht.notifyrelay.feature.appslist.AppRepository
import com.xzyht.notifyrelay.nativecore.NativeCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import notifyrelay.base.util.Logger
import notifyrelay.data.FilterConfigDefaults
import notifyrelay.data.StorageManager
import notifyrelay.data.database.entity.BlackListEntryEntity
import notifyrelay.data.database.entity.PackageGroupEntity
import notifyrelay.data.database.entity.WhiteListEntryEntity
import notifyrelay.data.database.repository.DatabaseRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * 远程过滤配置
 * 包含包名映射、智能去重（先发送后撤回机制）、黑白名单/对等模式配置
 * 名单类配置（黑白名单、包名组）存独立表，标量配置存 app_config
 */
object RemoteFilterConfig {
    private const val KEY_FILTER_MODE = "filter_mode"
    private const val KEY_ENABLE_DEDUP = "enable_dedup"
    private const val KEY_ENABLE_PEER = "enable_peer"

    /**
     * 保存串行化互斥锁：多个 UI 回调通过 scope.launch { save() } 并发触发时，
     * 用 Mutex.withLock 让保存排队执行，避免后触发者先完成、先触发者后完成
     * 覆盖回旧状态的竞态。注意必须用同一把跨实例锁（object 单例），
     * 因此声明在 object 顶层而非 save() 局部。
     */
    private val saveLock = Mutex()

    // 标记配置是否已加载，避免重复加载
    var isLoaded: Boolean = false

    // 包名等价功能总开关
    var enablePackageGroupMapping: Boolean = true
    val defaultPackageGroups: List<List<String>>
        get() = FilterConfigDefaults.defaultPackageGroups
    var defaultGroupEnabled: MutableList<Boolean> = mutableListOf(true, true, true)

    // 用户自定义包名等价组，每组为包名列表
    var customPackageGroups: MutableList<MutableList<String>> = mutableListOf()

    // 每个自定义组的开关
    var customGroupEnabled: MutableList<Boolean> = mutableListOf()

    // 合并后的包名等价组
    val packageGroups: List<Set<String>>
        get() =
            if (!enablePackageGroupMapping) {
                emptyList()
            } else {
                defaultPackageGroups.withIndex().filter { defaultGroupEnabled.getOrNull(it.index) == true }.map { it.value.toSet() } +
                    customPackageGroups.withIndex().filter { customGroupEnabled.getOrNull(it.index) == true }.map { it.value.toSet() }
            }

    // 智能去重开关（先发送后撤回机制）
    var enableDeduplication: Boolean = true

    // 黑白名单模式："none"=无，"black"=黑名单，"white"=白名单，"peer"=对等
    var filterMode: String = "none"

    // 黑名单内容（包名或通用包名+可选文本关键词），Pair<包名, 关键词?>
    var blackList: List<Pair<String, String?>> = emptyList()

    // 白名单内容（包名或通用包名+可选文本关键词），Pair<包名, 关键词?>
    var whiteList: List<Pair<String, String?>> = emptyList()

    // 黑/白名单的启用条目（序列化字符串集合，允许禁用单条规则）
    var blackListEnabled: Set<String> = emptySet()
    var whiteListEnabled: Set<String> = emptySet()

    // 对等模式开关（仅本机存在的应用或通用应用）
    var enablePeerMode: Boolean = false

    // 锁屏通知过滤开关
    var enableLockScreenOnly: Boolean = true

    // 加载设置
    suspend fun load(context: Context) {
        withContext(Dispatchers.IO) {
            enablePackageGroupMapping = StorageManager.getBoolean(context, "enable_package_group_mapping", true, StorageManager.PrefsType.FILTER)
            filterMode = StorageManager.getString(context, KEY_FILTER_MODE, "none", StorageManager.PrefsType.FILTER)
            enableDeduplication = StorageManager.getBoolean(context, KEY_ENABLE_DEDUP, true, StorageManager.PrefsType.FILTER)
            enablePeerMode = StorageManager.getBoolean(context, KEY_ENABLE_PEER, false, StorageManager.PrefsType.FILTER)
            enableLockScreenOnly = StorageManager.getBoolean(context, "enable_lock_screen_only", true, StorageManager.PrefsType.FILTER)
            loadFilterLists(context)
            loadPackageGroups(context)
            isLoaded = true
        }
    }

    /** 同步兜底：仅供 filterRemoteNotification 等无法挂起的同步入口使用 */
    fun loadBlocking(context: Context) = runBlocking { load(context) }

    private suspend fun loadFilterLists(context: Context) {
        withContext(Dispatchers.IO) {
            val repo =
                DatabaseRepository
                    .getInstance(context)
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
    }

    private suspend fun loadPackageGroups(context: Context) {
        withContext(Dispatchers.IO) {
            val repo =
                DatabaseRepository
                    .getInstance(context)
            val groups = repo.getPackageGroups()
            val items =
                repo
                    .getPackageGroupItems()
                    .groupBy { it.groupId }
                    .mapValues { (_, v) -> v.map { it.packageName } }
            val defaultRows = groups.filter { it.isDefault }.sortedBy { it.id }
            defaultGroupEnabled = MutableList(defaultPackageGroups.size) { true }
            defaultRows.forEachIndexed { idx, g ->
                if (idx < defaultGroupEnabled.size) defaultGroupEnabled[idx] = g.enabled
            }
            val customRows = groups.filter { !it.isDefault }.sortedBy { it.id }
            customPackageGroups = customRows.map { (items[it.id] ?: emptyList()).toMutableList() }.toMutableList()
            customGroupEnabled = customRows.map { it.enabled }.toMutableList()
        }
    }

    // 保存设置（优化性能）
    suspend fun save(context: Context) {
        // 并发串行化：UI 回调通过 scope.launch 并发触发 save 时，用 Mutex 排队，
        // 保证保存按触发顺序完成，最终持久化结果与最新 UI 状态一致。
        saveLock.withLock {
            withContext(Dispatchers.IO) {
                try {
                    StorageManager.putBoolean(context, "enable_package_group_mapping", enablePackageGroupMapping, StorageManager.PrefsType.FILTER)
                    StorageManager.putString(context, KEY_FILTER_MODE, filterMode, StorageManager.PrefsType.FILTER)
                    StorageManager.putBoolean(context, KEY_ENABLE_DEDUP, enableDeduplication, StorageManager.PrefsType.FILTER)
                    StorageManager.putBoolean(context, KEY_ENABLE_PEER, enablePeerMode, StorageManager.PrefsType.FILTER)
                    StorageManager.putBoolean(context, "enable_lock_screen_only", enableLockScreenOnly, StorageManager.PrefsType.FILTER)
                    saveFilterLists(context)
                    savePackageGroups(context)

                    // 保存后立即同步到 Rust 侧
                    val ctx = BackendRemoteFilter.rustContext
                    if (ctx != null) {
                        val installedPkgs = AppRepository.getInstalledPackageNamesSync(context)
                        syncToRust(ctx, installedPkgs)
                    }
                } catch (e: Exception) {
                    Logger.e("RemoteFilterConfig", "Failed to save configuration", e)
                }
            }
        }
    }

    /** 同步兜底：仅供无法挂起的同步入口使用（如 UI 回调未改协程处） */
    fun saveBlocking(context: Context) = runBlocking { save(context) }

    private suspend fun saveFilterLists(context: Context) {
        withContext(Dispatchers.IO) {
            val repo =
                DatabaseRepository
                    .getInstance(context)
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
    }

    private suspend fun savePackageGroups(context: Context) {
        withContext(Dispatchers.IO) {
            val repo =
                DatabaseRepository
                    .getInstance(context)
            val groups = mutableListOf<PackageGroupEntity>()
            val itemPackages = mutableListOf<List<String>>()
            defaultPackageGroups.forEachIndexed { idx, pkgs ->
                groups.add(
                    PackageGroupEntity(
                        groupName = "默认组${idx + 1}",
                        enabled = defaultGroupEnabled.getOrNull(idx) ?: true,
                        isDefault = true,
                    ),
                )
                itemPackages.add(pkgs)
            }
            customPackageGroups.forEachIndexed { idx, pkgs ->
                groups.add(
                    PackageGroupEntity(
                        groupName = "自定义组${idx + 1}",
                        enabled = customGroupEnabled.getOrNull(idx) ?: true,
                        isDefault = false,
                    ),
                )
                itemPackages.add(pkgs)
            }
            repo.replacePackageGroups(groups, itemPackages)
        }
    }

    /** 将当前配置同步到 Rust Core */
    fun syncToRust(
        ctx: Pointer,
        installedPkgs: Set<String>,
    ): Boolean {
        val json = buildRustConfigJson(installedPkgs)
        return NativeCore.setFilterConfig(ctx, json) == 0
    }

    // ---------- 黑白名单操作（按当前 filterMode 生效） ----------

    private fun serializeFilterEntry(
        pkg: String,
        keyword: String?,
    ): String = pkg + (keyword?.takeIf { it.isNotBlank() }?.let { "|$it" } ?: "")

    /** 当前模式对应的名单 */
    fun getActiveFilterList(): List<Pair<String, String?>> =
        when (filterMode) {
            "black" -> blackList
            "white" -> whiteList
            else -> emptyList()
        }

    /** 条目是否启用（迁移后默认启用） */
    fun isActiveEntryEnabled(
        pkg: String,
        keyword: String,
    ): Boolean {
        val ser = serializeFilterEntry(pkg, keyword)
        return if (filterMode == "black") {
            blackListEnabled.contains(ser)
        } else if (filterMode == "white") {
            whiteListEnabled.contains(ser)
        } else {
            true
        }
    }

    /** 向当前模式的名单添加条目（默认启用） */
    suspend fun addFilterEntry(
        context: Context,
        pkg: String,
        keyword: String,
    ) {
        val kw = keyword.takeIf { it.isNotBlank() }
        when (filterMode) {
            "black" -> {
                blackList = blackList + (pkg to kw)
                blackListEnabled = blackListEnabled + serializeFilterEntry(pkg, kw)
            }
            "white" -> {
                whiteList = whiteList + (pkg to kw)
                whiteListEnabled = whiteListEnabled + serializeFilterEntry(pkg, kw)
            }
            else -> return
        }
        save(context)
    }

    /** 从当前模式的名单移除条目 */
    suspend fun removeFilterEntry(
        context: Context,
        pkg: String,
        keyword: String,
    ) {
        val ser = serializeFilterEntry(pkg, keyword)
        when (filterMode) {
            "black" -> {
                blackList = blackList.filterNot { it.first == pkg && (it.second ?: "") == keyword }
                blackListEnabled = blackListEnabled - ser
            }
            "white" -> {
                whiteList = whiteList.filterNot { it.first == pkg && (it.second ?: "") == keyword }
                whiteListEnabled = whiteListEnabled - ser
            }
        }
        save(context)
    }

    /** 启用/禁用当前模式的条目 */
    suspend fun setFilterEntryEnabled(
        context: Context,
        pkg: String,
        keyword: String,
        enabled: Boolean,
    ) {
        val ser = serializeFilterEntry(pkg, keyword)
        when (filterMode) {
            "black" -> blackListEnabled = if (enabled) blackListEnabled + ser else blackListEnabled - ser
            "white" -> whiteListEnabled = if (enabled) whiteListEnabled + ser else whiteListEnabled - ser
        }
        save(context)
    }

    /** 构建 Rust nrc_set_filter_config 所需的 JSON */
    private fun buildRustConfigJson(installedPkgs: Set<String>): String {
        val root = JSONObject()

        root.put("enablePackageGroupMapping", enablePackageGroupMapping)

        // 包名组：使用连续索引
        val pkgGroupsArr = JSONArray()
        val enabledMap = JSONObject()
        var groupIdx = 0
        for ((i, group) in defaultPackageGroups.withIndex()) {
            val enabled = defaultGroupEnabled.getOrNull(i) ?: true
            if (enabled) {
                val g = JSONObject()
                g.put("groupName", "group_$groupIdx")
                g.put("packages", JSONArray(group))
                pkgGroupsArr.put(g)
                enabledMap.put("group_$groupIdx", true)
                groupIdx++
            }
        }
        for ((i, group) in customPackageGroups.withIndex()) {
            val enabled = customGroupEnabled.getOrNull(i) ?: true
            if (enabled) {
                val g = JSONObject()
                g.put("groupName", "group_$groupIdx")
                g.put("packages", JSONArray(group))
                pkgGroupsArr.put(g)
                enabledMap.put("group_$groupIdx", true)
                groupIdx++
            }
        }
        root.put("packageGroups", pkgGroupsArr)
        root.put("groupEnabled", enabledMap)

        // 过滤模式
        val filterModeNum =
            when (filterMode) {
                "white" -> 1
                "black" -> 2
                else -> 0
            }
        root.put("filterMode", filterModeNum)

        // 黑白名单（当前模式对应的名单，仅包含启用条目）
        val activeList =
            when (filterMode) {
                "black" -> blackList
                "white" -> whiteList
                else -> emptyList()
            }
        val activeEnabled =
            when (filterMode) {
                "black" -> blackListEnabled
                "white" -> whiteListEnabled
                else -> emptySet()
            }
        val filterListArr = JSONArray()
        for ((pkg, keyword) in activeList) {
            if (activeEnabled.contains(serializeFilterEntry(pkg, keyword))) {
                filterListArr.put(serializeFilterEntry(pkg, keyword))
            }
        }
        root.put("filterList", filterListArr)

        root.put("enablePeerMode", enablePeerMode)
        root.put("installedPackages", JSONArray(installedPkgs.toList()))

        return root.toString()
    }

    /** 包名映射 — 委托给 Rust Core */
    fun mapToLocalPackage(
        pkg: String,
        installedPkgs: Set<String>,
    ): String {
        val ctx = BackendRemoteFilter.rustContext ?: return pkg
        return NativeCore.mapLocalPackage(ctx, pkg) ?: pkg
    }

    /** 检查过滤模式（含关键词匹配）— 委托给 Rust Core */
    fun checkFilterWithRust(
        pkg: String,
        title: String,
        text: String,
    ): Boolean {
        val ctx = BackendRemoteFilter.rustContext ?: return true
        return NativeCore.checkFilterMode(ctx, pkg, pkg, title, text)
    }
}
