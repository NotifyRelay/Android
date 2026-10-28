package notifyrelay.shared.feature.appslist

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import notifyrelay.shared.generated.resources.Res
import notifyrelay.shared.generated.resources.app_icon
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.jetbrains.compose.resources.imageResource

/**
 * 应用图标的唯一来源。
 *
 * 图标文件随 :shared 的 composeResources 分发（`composeResources/drawable/app_icon.webp`），
 * Android 与桌面端共用同一份资源，不再各自维护一套图标。
 *
 * - Composable 场景用 [bitmap]（走 Compose 资源加载与缓存）
 * - 非 Composable 场景（如窗口图标）用 [bytes] 自行解码
 */
object SharedAppIcon {
    @Volatile
    private var cachedBytes: ByteArray? = null

    /** 在 Composable 中取图标位图。 */
    @Composable
    fun bitmap(): ImageBitmap = imageResource(Res.drawable.app_icon)

    /**
     * 同步取已缓存的图标字节；首次调用返回 null。
     *
     * 需要首帧即有图标时，先调用 [preload] 预热缓存。
     */
    fun bytesOrNull(): ByteArray? = cachedBytes

    /** 预加载图标字节（suspend）。 */
    suspend fun preload(): ByteArray? {
        cachedBytes?.let { return it }
        val bytes =
            runCatching {
                getDrawableResourceBytes(getSystemResourceEnvironment(), Res.drawable.app_icon)
            }.getOrNull()
        cachedBytes = bytes
        return bytes
    }

    /** 在给定作用域中异步预加载。 */
    fun preloadIn(scope: CoroutineScope) {
        if (cachedBytes != null) return
        scope.launch { preload() }
    }
}
