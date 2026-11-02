package notifyrelay.data.filter

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import notifyrelay.data.database.entity.FilterEntryEntity
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.domain.feature.notification.filter.FilterEntry

/**
 * 本机通知过滤条目仓库。
 *
 * 只承载条目与开关的读写；「本机通知是否应转发」的判定依赖 Android 通知对象，留在平台侧。
 * 表为空时写入内置条目；内置条目不可删除。
 */
object LocalFilterRepository {
    /** 过滤本应用通知。 */
    var filterSelf: Boolean = true

    /** 过滤持久化（常驻）通知。 */
    var filterOngoing: Boolean = true

    /** 过滤无标题或无内容通知。 */
    var filterNoTitleOrText: Boolean = true

    /** 过滤重要性为无的通知。 */
    var filterImportanceNone: Boolean = true

    /** 内置过滤条目（文本关键词与默认包名，均为不可删除的默认黑名单）。 */
    private val builtinFilterEntries: Set<FilterEntry> =
        setOf(
            FilterEntry("米家 设备状态", ""),
            FilterEntry("米家 手表", ""),
            FilterEntry("应用商店 正在安装", ""),
            FilterEntry("新消息 你有一条新消息", ""),
            FilterEntry("已隐藏敏感通知", ""),
            FilterEntry("查找 正在处理", ""),
            FilterEntry("电话 正在通话录音", ""),
            FilterEntry("正在使用妙享桌面", ""),
            FilterEntry("录音机 正在录音", ""),
            FilterEntry("录音机 录音已完成", ""),
            FilterEntry("小米汽车互联服务 小米汽车互联服务", ""),
            FilterEntry("您有一条新消息 请点击查看", ""),
            FilterEntry("正在运行", ""),
            FilterEntry("服务运行中", ""),
            FilterEntry("后台运行", ""),
            FilterEntry("点按即可了解详情", ""),
            FilterEntry("正在同步", ""),
            FilterEntry("运行中", ""),
            FilterEntry("service running", ""),
            FilterEntry("is running", ""),
            FilterEntry("tap for more info", ""),
            // 默认包名作为内置条目
            FilterEntry("", "com.miui.systemAdSolution"),
        )

    private val saveLock = Mutex()

    @Volatile
    private var cachedRows: List<FilterEntryEntity>? = null

    /** 内置关键词集合。 */
    fun getBuiltinKeywords(): Set<String> = builtinFilterEntries.filter { it.keyword.isNotBlank() }.map { it.keyword }.toSet()

    /** 默认包名过滤集合。 */
    fun getDefaultPackageFilters(): Set<String> = builtinFilterEntries.filter { it.packageName.isNotBlank() }.map { it.packageName }.toSet()

    /** 全部过滤条目（含内置条目），表为空时初始化内置条目。 */
    suspend fun getFilterEntries(): Set<FilterEntry> = rowsCached().map { FilterEntry(it.keyword, it.packageName) }.toSet()

    /** 已启用的过滤条目（允许用户禁用内置条目）。 */
    suspend fun getEnabledFilterEntries(): Set<FilterEntry> = rowsCached().filter { it.enabled }.map { FilterEntry(it.keyword, it.packageName) }.toSet()

    /** 设置条目启用状态。 */
    suspend fun setFilterEntryEnabled(
        entry: FilterEntry,
        enabled: Boolean,
    ) {
        saveLock.withLock {
            val repo = DatabaseRepository.getInstance()
            val rows = repo.getLocalFilterEntries().toMutableList()
            val idx = rows.indexOfFirst { it.keyword == entry.keyword && it.packageName == entry.packageName }
            if (idx >= 0) {
                rows[idx] = rows[idx].copy(enabled = enabled)
            } else {
                rows.add(FilterEntryEntity(entry.keyword, entry.packageName, enabled))
            }
            repo.replaceLocalFilterEntries(rows)
            invalidateCache()
        }
    }

    /** 新增条目（已存在同条目时不重复添加）。 */
    suspend fun addFilterEntry(
        keyword: String,
        packageName: String,
    ) {
        saveLock.withLock {
            val entry = FilterEntry(keyword.trim(), packageName.trim())
            val repo = DatabaseRepository.getInstance()
            val rows = repo.getLocalFilterEntries().toMutableList()
            if (rows.none { it.keyword == entry.keyword && it.packageName == entry.packageName }) {
                rows.add(FilterEntryEntity(entry.keyword, entry.packageName, true))
                repo.replaceLocalFilterEntries(rows)
            }
            invalidateCache()
        }
    }

    /** 删除条目；内置条目不可删除。 */
    suspend fun removeFilterEntry(
        keyword: String,
        packageName: String,
    ) {
        if ((packageName.isBlank() && builtinFilterEntries.any { it.keyword == keyword && it.keyword.isNotBlank() }) ||
            (keyword.isBlank() && builtinFilterEntries.any { it.packageName == packageName && it.packageName.isNotBlank() })
        ) {
            return
        }
        saveLock.withLock {
            val repo = DatabaseRepository.getInstance()
            val rows = repo.getLocalFilterEntries().filterNot { it.keyword == keyword && it.packageName == packageName }
            repo.replaceLocalFilterEntries(rows)
            invalidateCache()
        }
    }

    /** 已启用的包名过滤集合。 */
    suspend fun getEnabledPackageFilters(): Set<String> = getEnabledFilterEntries().filter { it.keyword.isBlank() && it.packageName.isNotBlank() }.map { it.packageName }.toSet()

    /** 设置包名条目启用状态。 */
    suspend fun setPackageEnabled(
        packageName: String,
        enabled: Boolean,
    ) {
        setFilterEntryEnabled(FilterEntry("", packageName), enabled)
    }

    /**
     * 本机通知是否应转发给对端。
     *
     * 对齐 Windows `BackendLocalFilter.ShouldForward`：
     * 1. 自身通知（包名或应用名含 `notifyrelay`）；
     * 2. 启用条目命中（关键词匹配标题/内容、包名匹配包名，二者兼有时需同时命中）；
     * 3. 标题与内容均为空。
     *
     * 「过滤无标题或无内容」按 Windows 语义取「二者皆空」，而非任一为空。
     *
     * @param packageName 通知所属包名（已映射为远端包名时的结果同样适用）
     * @param appName 通知所属应用显示名
     * @param title 通知标题
     * @param text 通知内容
     */
    suspend fun shouldForward(
        packageName: String,
        appName: String,
        title: String,
        text: String,
    ): Boolean {
        val enabledEntries = getEnabledFilterEntries()
        return LocalForwardRules.shouldForward(
            filterSelf = filterSelf,
            filterNoTitleOrText = filterNoTitleOrText,
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            enabledEntries = enabledEntries,
        )
    }

    private suspend fun rowsCached(): List<FilterEntryEntity> =
        saveLock.withLock {
            cachedRows?.let { return@withLock it }
            val repo = DatabaseRepository.getInstance()
            val rows = repo.getLocalFilterEntries()
            if (rows.isEmpty()) {
                val builtins = builtinFilterEntries.map { FilterEntryEntity(it.keyword, it.packageName, true) }
                repo.replaceLocalFilterEntries(builtins)
                builtins.also { cachedRows = it }
                return@withLock builtins
            }
            rows.also { cachedRows = it }
            rows
        }

    private fun invalidateCache() {
        cachedRows = null
    }
}

/**
 * 本机通知转发的纯判定规则。
 *
 * 与 [LocalFilterRepository] 分离以便脱离数据库单测；规则逐条对齐 Windows
 * `BackendLocalFilter.ShouldForward` 与 `FilterEntry.Matches`。
 */
internal object LocalForwardRules {
    /** 自身应用标识：包名或应用名含该串即视为自身通知。 */
    private const val SELF_INDICATOR = "notifyrelay"

    /**
     * 条目是否命中通知。
     *
     * 两者均为空时该条目无效；仅关键词时只看标题/内容；仅包名时只看包名；
     * 二者兼有时需同时命中。
     */
    fun matches(
        entry: FilterEntry,
        packageName: String,
        title: String,
        text: String,
    ): Boolean {
        val hasKeyword = entry.keyword.isNotBlank()
        val hasPackage = entry.packageName.isNotBlank()
        if (!hasKeyword && !hasPackage) return false

        val keywordMatch = !hasKeyword || title.contains(entry.keyword, ignoreCase = true) || text.contains(entry.keyword, ignoreCase = true)
        val packageMatch = !hasPackage || packageName.contains(entry.packageName, ignoreCase = true)

        return when {
            hasKeyword && hasPackage -> keywordMatch && packageMatch
            hasKeyword -> keywordMatch
            else -> packageMatch
        }
    }

    /** 是否应转发；规则顺序与 Windows 一致，短路返回。 */
    fun shouldForward(
        filterSelf: Boolean,
        filterNoTitleOrText: Boolean,
        packageName: String,
        appName: String,
        title: String,
        text: String,
        enabledEntries: Set<FilterEntry>,
    ): Boolean {
        // 1. 自身通知
        if (filterSelf && isSelfNotification(packageName, appName)) return false

        // 2. 启用条目命中
        if (enabledEntries.any { matches(it, packageName, title, text) }) return false

        // 3. 标题与内容均为空
        if (filterNoTitleOrText && title.isBlank() && text.isBlank()) return false

        return true
    }

    /** 包名或应用名含自身标识即视为自身通知。 */
    fun isSelfNotification(
        packageName: String,
        appName: String,
    ): Boolean = packageName.contains(SELF_INDICATOR, ignoreCase = true) || appName.contains(SELF_INDICATOR, ignoreCase = true)
}
