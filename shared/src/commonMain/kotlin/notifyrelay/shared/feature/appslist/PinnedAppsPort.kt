package notifyrelay.shared.feature.appslist

import kotlinx.coroutines.flow.StateFlow
import notifyrelay.data.repository.applist.PinnedAppsRepository

/**
 * 应用置顶状态端口。
 *
 * 公共端 UI 依赖本接口；平台侧以实现注入。
 */
interface PinnedAppsPort {
    /** 设备 UUID → 已置顶包名集合。 */
    val pinnedApps: StateFlow<Map<String, Set<String>>>

    /** 加载指定设备的置顶列表。 */
    fun load(deviceUuid: String)

    /** 置顶应用。 */
    fun pin(
        deviceUuid: String,
        packageName: String,
    )

    /** 取消置顶。 */
    fun unpin(
        deviceUuid: String,
        packageName: String,
    )

    /** 是否已置顶。 */
    fun isPinned(
        deviceUuid: String,
        packageName: String,
    ): Boolean
}

/**
 * 以 [PinnedAppsRepository] 为唯一来源的置顶端口实现。
 *
 * 两端没有平台差异：置顶状态存在公共数据层，UI 与远端列表读的是同一份。
 */
object RepositoryPinnedAppsPort : PinnedAppsPort {
    override val pinnedApps: StateFlow<Map<String, Set<String>>> get() = PinnedAppsRepository.pinnedApps

    override fun load(deviceUuid: String) = PinnedAppsRepository.loadPinnedApps(deviceUuid)

    override fun pin(
        deviceUuid: String,
        packageName: String,
    ) = PinnedAppsRepository.pinApp(deviceUuid, packageName)

    override fun unpin(
        deviceUuid: String,
        packageName: String,
    ) = PinnedAppsRepository.unpinApp(deviceUuid, packageName)

    override fun isPinned(
        deviceUuid: String,
        packageName: String,
    ): Boolean = PinnedAppsRepository.isAppPinned(deviceUuid, packageName)
}
