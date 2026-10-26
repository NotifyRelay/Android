package notifyrelay.shared.feature.appslist

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.shared.feature.device.model.DeviceInfo

/**
 * 应用图标能力端口。
 *
 * 公共端 UI 依赖本接口；平台侧以实际图标实现并在启动时注册。
 */
interface AppIconPort {
    /** 图标更新信号：包名 + 时间戳。 */
    val iconUpdates: StateFlow<Pair<String, Long>?>

    /** 取本机应用图标。 */
    suspend fun localIcon(packageName: String): ImageBitmap?

    /** 取外部（对端）应用图标。 */
    suspend fun externalIcon(packageName: String): ImageBitmap?

    /** 批量取外部应用图标。 */
    suspend fun externalIcons(packageNames: List<String>): Map<String, ImageBitmap?>

    /** 取图标，缺失时自动向对端请求。 */
    suspend fun iconWithAutoRequest(
        packageName: String,
        sourceDevice: DeviceInfo? = null,
    ): ImageBitmap?

    /** 缓存外部应用图标。 */
    suspend fun cacheExternalIcon(
        packageName: String,
        icon: ImageBitmap?,
        deviceUuid: String,
    )
}
