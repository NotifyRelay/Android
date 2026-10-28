package notifyrelay.desktop.platform

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import java.awt.Image as AwtImage

/**
 * 窗口图标构建。
 *
 * 把 :shared 的图标字节解码后输出多种尺寸，交给 `Window.setIconImages`。
 * Windows 标题栏会按当前 DPI 自行挑选最合适的一档，避免单张图被放大而模糊。
 */
object WindowIcon {
    /** 常见标题栏与任务栏尺寸。 */
    private val SIZES = intArrayOf(16, 20, 24, 32, 40, 48, 64, 128, 256)

    /**
     * 由图标字节构建多尺寸 AWT 图像列表。
     *
     * @param bytes PNG/WebP 等格式的图标字节
     * @return 按尺寸升序排列的图像列表；解码失败时返回空列表
     */
    fun buildMultiSize(bytes: ByteArray): List<AwtImage> {
        val source = decode(bytes) ?: return emptyList()
        return SIZES.map { size -> source.scaledTo(size) }
    }

    /** 解码为 BufferedImage；优先 Skia（可解 WebP），失败再试 ImageIO。 */
    private fun decode(bytes: ByteArray): BufferedImage? {
        runCatching { Image.makeFromEncoded(bytes) }.getOrNull()?.let { skia ->
            val bitmap = Bitmap()
            bitmap.allocN32Pixels(skia.width, skia.height)
            skia.readPixels(bitmap)
            return bitmap.toBufferedImage()
        }
        return ImageIO.read(ByteArrayInputStream(bytes))
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

    /** 高质量缩放到指定边长。 */
    private fun BufferedImage.scaledTo(size: Int): BufferedImage {
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(
                java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC,
            )
            g.setRenderingHint(
                java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY,
            )
            g.setRenderingHint(
                java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON,
            )
            g.drawImage(this, 0, 0, size, size, null)
        } finally {
            g.dispose()
        }
        return out
    }
}
