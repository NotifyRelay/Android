package notifyrelay.desktop.platform

import kotlinx.coroutines.runBlocking
import notifyrelay.base.log.Logger
import notifyrelay.shared.feature.appslist.SharedAppIcon
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Data
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import java.awt.image.BufferedImage

/**
 * 应用图标（矢量源）。
 *
 * 矢量源是 :shared 的 `composeResources/drawable/app_icon.svg`，与 Android 共用同一份；
 * 桌面端按目标尺寸即时栅格化，不维护多档位像素图。
 */
object AppIcon {
    private const val TAG = "DesktopAppIcon"

    /** 图标底色，与 Android 自适应图标背景同值。 */
    private const val BACKGROUND_COLOR = 0xFF0067B8.toInt()

    /** 文档未声明尺寸时使用的边长。 */
    private const val DEFAULT_DOCUMENT_SIZE = 96f

    /** 底色圆角半径占边长的比例。 */
    private const val CORNER_RADIUS_RATIO = 0.22f

    /** 常见标题栏与任务栏尺寸。 */
    private val SIZES = intArrayOf(16, 20, 24, 32, 40, 48, 64, 128, 256)

    /**
     * 构建多尺寸窗口图标。
     *
     * @return 按尺寸升序排列的图像列表；矢量源不可用时返回空列表
     */
    fun images(): List<BufferedImage> {
        val svg = loadSvg() ?: return emptyList()
        return SIZES.map { size -> runCatching { render(svg, size) }.getOrNull() }.filterNotNull()
    }

    /** 读取 :shared 的矢量源；不可用时返回 null（供其它图标场景复用）。 */
    fun loadSvgBytes(): ByteArray? = loadSvg()

    /** 读取 :shared 的矢量源；不可用时记日志并返回 null。 */
    private fun loadSvg(): ByteArray? =
        runCatching { runBlocking { SharedAppIcon.preload() } }.getOrElse {
            Logger.w(TAG, "读取矢量图标失败", it)
            null
        }

    /**
     * 用给定矢量源渲染图标，供构建期图标生成复用。
     *
     * @param svg 矢量源字节
     * @param size 边长（像素）
     */
    fun render(
        svg: ByteArray,
        size: Int,
    ): BufferedImage {
        require(size > 0) { "size 必须为正数" }
        val dom = SVGDOM(Data.makeFromBytes(svg))
        val surface = Surface.makeRasterN32Premul(size, size)
        try {
            drawBackground(surface, size)
            // 矢量源与背景同尺寸（96x96 且内容铺满），
            // 直接按画布尺寸等比铺满即可，无需额外的图层缩放。
            val documentWidth =
                dom.root
                    ?.viewBox
                    ?.width
                    ?.takeIf { it > 0f } ?: DEFAULT_DOCUMENT_SIZE
            val documentHeight =
                dom.root
                    ?.viewBox
                    ?.height
                    ?.takeIf { it > 0f } ?: DEFAULT_DOCUMENT_SIZE
            // 取较小的缩放比，保证整幅图形完整落在画布内而不被裁切
            val scale = minOf(size / documentWidth, size / documentHeight)
            val dx = (size - documentWidth * scale) / 2f
            val dy = (size - documentHeight * scale) / 2f
            surface.canvas.translate(dx, dy)
            surface.canvas.scale(scale, scale)
            dom.render(surface.canvas)
            val bitmap = Bitmap()
            bitmap.allocN32Pixels(size, size)
            surface.makeImageSnapshot().readPixels(bitmap)
            return bitmap.toBufferedImage()
        } finally {
            surface.close()
            dom.close()
        }
    }

    /** 绘制圆角底色，与 Android 自适应图标的背景色一致。 */
    private fun drawBackground(
        surface: Surface,
        size: Int,
    ) {
        val radius = size * CORNER_RADIUS_RATIO
        val paint = Paint().apply { color = BACKGROUND_COLOR }
        surface.canvas.drawRRect(
            RRect.makeXYWH(0f, 0f, size.toFloat(), size.toFloat(), radius),
            paint,
        )
    }

    /** Skia Bitmap 转 BufferedImage。 */
    private fun Bitmap.toBufferedImage(): BufferedImage {
        val w = width
        val h = height
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = getColor(x, y)
            }
        }
        out.setRGB(0, 0, w, h, pixels, 0, w)
        return out
    }
}
