package notifyrelay.domain.feature.notification.filter

/**
 * 本机通知过滤端口。
 *
 * 过滤开关与条目读写均经本接口；实现位于平台侧。
 */
interface LocalFilterPort {
    /** 过滤本应用通知。 */
    var filterSelf: Boolean

    /** 过滤持久化通知。 */
    var filterOngoing: Boolean

    /** 过滤无标题或无内容。 */
    var filterNoTitleOrText: Boolean

    /** 过滤重要性为无的通知。 */
    var filterImportanceNone: Boolean

    /** 内置关键词集合。 */
    fun builtinKeywords(): Set<String>

    /** 内置包名集合。 */
    fun defaultPackageFilters(): Set<String>

    /** 全部过滤条目。 */
    suspend fun entries(): Set<FilterEntry>

    /** 已启用的过滤条目。 */
    suspend fun enabledEntries(): Set<FilterEntry>

    /** 设置条目启用状态。 */
    suspend fun setEntryEnabled(
        entry: FilterEntry,
        enabled: Boolean,
    )

    /** 添加条目。 */
    suspend fun addEntry(
        keyword: String,
        packageName: String,
    )

    /** 删除条目。 */
    suspend fun removeEntry(
        keyword: String,
        packageName: String,
    )
}

/** [LocalFilterPort] 的注册点。 */
object LocalFilterPortRegistry {
    @Volatile
    var current: LocalFilterPort? = null
        private set

    fun install(port: LocalFilterPort) {
        current = port
    }
}
