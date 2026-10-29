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
