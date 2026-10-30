package notifyrelay.shared.feature.appslist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import notifyrelay.shared.generated.resources.Res
import notifyrelay.shared.generated.resources.app_icon
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.jetbrains.compose.resources.painterResource

/**
 * 应用图标的唯一来源。
 *
 * 图标以矢量形式随 :shared 的 composeResources 分发
 * （`composeResources/drawable/app_icon.svg`），Android 与桌面端共用同一份资源。
 *
 * - Composable 场景用 [bitmap]（由矢量绘制到位图，保持既有 ImageBitmap 调用点）
 * - 非 Composable 场景（窗口图标、打包图标）用 [preload] 取矢量字节自行栅格化
 */
object SharedAppIcon {
    /** 绘制位图时的默认边长。 */
    private const val DEFAULT_BITMAP_SIZE = 96

    @Volatile
    private var cachedBytes: ByteArray? = null

    /**
     * 在 Composable 中取图标位图。
     *
     * @param size 位图边长（像素）
     */
    @Composable
    fun bitmap(size: Int = DEFAULT_BITMAP_SIZE): ImageBitmap {
        val painter = painterResource(Res.drawable.app_icon)
        return remember(painter, size) { painter.toImageBitmap(size) }
    }

    /**
     * 同步取已缓存的图标字节；首次调用返回 null。
     *
     * 需要首帧即有图标时，先调用 [preload] 预热缓存。
     */
    fun bytesOrNull(): ByteArray? = cachedBytes

    /** 预加载矢量图标字节（suspend）。 */
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

    /** 把矢量绘制成指定边长的位图。 */
    private fun Painter.toImageBitmap(size: Int): ImageBitmap {
        val image = ImageBitmap(size, size)
        val canvas = Canvas(image)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            canvas,
            Size(size.toFloat(), size.toFloat()),
        ) {
            with(this@toImageBitmap) { draw(this@draw.size) }
        }
        return image
    }
}
