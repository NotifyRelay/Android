package notifyrelay.domain.feature.notification.filter

/**
 * 远端通知过滤端口。
 *
 * 过滤模式、去重、包名等价映射与黑白名单均经本接口；实现位于平台侧。
 */
interface RemoteFilterPort {
    /** 过滤模式：none / black / white / peer。 */
    var filterMode: String

    /** 智能去重。 */
    var enableDedup: Boolean

    /** 包名等价映射。 */
    var enablePackageGroupMapping: Boolean

    /** 仅复刻锁屏通知。 */
    var enableLockScreenOnly: Boolean

    /** 默认包名组。 */
    val defaultPackageGroups: List<List<String>>

    /** 默认组启用状态。 */
    var defaultGroupEnabled: List<Boolean>

    /** 自定义包名组。 */
    var customPackageGroups: List<List<String>>

    /** 自定义组启用状态。 */
    var customGroupEnabled: List<Boolean>

    /** 配置是否已加载。 */
    var isLoaded: Boolean

    /** 加载配置。 */
    suspend fun load()

    /** 保存配置。 */
    suspend fun save()

    /** 当前过滤名单（包名 to 关键词）。 */
    fun activeFilterList(): List<Pair<String, String?>>

    /** 判断条目是否启用。 */
    fun isActiveEntryEnabled(
        packageName: String,
        keyword: String,
    ): Boolean

    /** 设置条目启用状态。 */
    suspend fun setEntryEnabled(
        packageName: String,
        keyword: String,
        enabled: Boolean,
    )

    /** 添加条目。 */
    suspend fun addEntry(
        packageName: String,
        keyword: String,
    )

    /** 删除条目。 */
    suspend fun removeEntry(
        packageName: String,
        keyword: String,
    )

    /** 把包名映射到本机等价包名。 */
    fun mapToLocalPackage(
        packageName: String,
        installedPackages: Set<String>,
    ): String
}

/** [RemoteFilterPort] 的注册点。 */
object RemoteFilterPortRegistry {
    @Volatile
    var current: RemoteFilterPort? = null
        private set

    fun install(port: RemoteFilterPort) {
        current = port
    }
}
