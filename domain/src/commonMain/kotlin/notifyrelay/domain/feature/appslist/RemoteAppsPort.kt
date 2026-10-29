package notifyrelay.domain.feature.appslist

import kotlinx.coroutines.flow.StateFlow
import notifyrelay.domain.feature.appslist.model.RemoteAppInfo
import notifyrelay.domain.feature.device.model.DeviceInfo

/**
 * 远端应用列表端口。
 *
 * 列表读取、置顶、刷新请求与启动应用均经本接口；实现位于平台侧。
 */
interface RemoteAppsPort {
    /** 图标更新信号。 */
    val iconUpdates: StateFlow<Pair<String, Long>?>

    /** 置顶状态（设备 UUID → 包名集合）。 */
    val pinnedApps: StateFlow<Map<String, Set<String>>>

    /** 读取远端应用列表。 */
    suspend fun remoteApps(deviceUuid: String): List<RemoteAppInfo>

    /** 加载指定设备的置顶状态。 */
    fun loadPinned(deviceUuid: String)

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

    /** 向设备请求最新应用列表；设备信息由实现自行解析。 */
    suspend fun requestAppList(deviceUuid: String): Boolean

    /** 解析在线设备信息；不存在返回 null。 */
    fun findOnlineDevice(deviceUuid: String): DeviceInfo?

    /**
     * 在远端设备上启动应用。
     *
     * @param app 目标应用
     * @param deviceUuid 应用所属设备的 UUID（据此解析对应的 adb 设备）
     * @param useScrcpyStartApp 是否经 scrcpy 的 `--start-app` 拉起
     */
    fun launchApp(
        app: RemoteAppInfo,
        deviceUuid: String,
        useScrcpyStartApp: Boolean,
    )
}

/** [RemoteAppsPort] 的注册点。 */
object RemoteAppsPortRegistry {
    @Volatile
    var current: RemoteAppsPort? = null
        private set

    fun install(port: RemoteAppsPort) {
        current = port
    }
}
