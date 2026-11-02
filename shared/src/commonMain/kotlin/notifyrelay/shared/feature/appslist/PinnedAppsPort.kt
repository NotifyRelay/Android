package notifyrelay.shared.feature.appslist

import kotlinx.coroutines.flow.StateFlow

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
