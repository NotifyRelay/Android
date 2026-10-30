package notifyrelay.desktop.platform

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.xzyht.notifyrelay.nativecore.device.appsync.IconCodecPort
import notifyrelay.base.util.decodeImageBitmap

/**
 * 图标编解码端口（桌面）。
 *
 * 解码时裁掉四周全透明的留白：Windows 侧 `DisplayInfo.GetLogo` 与应用图标常带
 * 大片透明边距（实测「截图工具」的有效内容只占原图 35%），按原图缩放会让可见
 * 内容显得很小。裁到有效内容后，界面即可按容器尺寸把图标放大显示。
 */
object DesktopIconCodecPort : IconCodecPort {
    /** 判定为透明的 alpha 上限。 */
    private const val ALPHA_THRESHOLD = 8

    override fun decodePng(bytes: ByteArray): Any? = decodeIconBitmap(bytes)

    override fun encodePng(platformBitmap: Any): ByteArray? = null

    override suspend fun localIconBytes(packageName: String): ByteArray? = null

    /** 把 PNG 字节解码为位图（含裁边），供端口实现复用。 */
    fun decodeToBitmap(bytes: ByteArray): ImageBitmap? = decodeIconBitmap(bytes)

    /**
     * 解码图标并裁掉透明留白。
     *
     * 无法解码或整图透明时返回 null；无留白时原样返回解码结果。
     */
    fun decodeIconBitmap(bytes: ByteArray): ImageBitmap? {
        val source = decodeImageBitmap(bytes) ?: return null
        val bounds = source.alphaContentBounds() ?: return null
        if (bounds.left == 0 && bounds.top == 0 && bounds.width == source.width && bounds.height == source.height) {
            return source
        }
        return source.cropTo(bounds)
    }

    /** 扫描 alpha 求有效内容矩形；无有效像素时返回 null。 */
    private fun ImageBitmap.alphaContentBounds(): IntRect? {
        val pixels = toPixelMap()
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val alpha = (pixels[x, y].toArgb() ushr 24) and 0xFF
                if (alpha > ALPHA_THRESHOLD) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) return null
        return IntRect(minX, minY, maxX + 1, maxY + 1)
    }

    /** 按给定矩形裁剪位图。 */
    private fun ImageBitmap.cropTo(bounds: IntRect): ImageBitmap {
        val target = ImageBitmap(bounds.width, bounds.height)
        val canvas = Canvas(target)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            canvas,
            Size(bounds.width.toFloat(), bounds.height.toFloat()),
        ) {
            drawImage(
                image = this@cropTo,
                srcOffset = IntOffset(bounds.left, bounds.top),
                srcSize = IntSize(bounds.width, bounds.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(bounds.width, bounds.height),
            )
        }
        return target
    }
}
