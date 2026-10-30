package notifyrelay.desktop.tool

import notifyrelay.desktop.platform.AppIcon
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 构建期图标生成：把矢量源渲染为 Windows 图标文件（.ico）。
 *
 * 用法：`IconTool <svg 路径> <ico 输出路径>`
 */
object IconTool {
    /** 需要写入的图标尺寸。 */
    private val SIZES = intArrayOf(16, 20, 24, 32, 40, 48, 64, 128, 256)

    /** PNG 编码的最大尺寸，其余尺寸写 32 位 BMP 条目。 */
    private const val PNG_SIZE = 256

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "用法: IconTool <svg> <ico>" }
        val svg = File(args[0]).readBytes()
        val images = SIZES.map { size -> size to AppIcon.render(svg, size) }
        val ico = buildIco(images)
        File(args[1]).apply {
            parentFile?.mkdirs()
            writeBytes(ico)
        }
        println("图标已生成: " + args[1] + " (" + ico.size + " 字节)")
    }

    /** 按 ICO 容器格式打包：256 用 PNG 条目，其余写 32 位 BMP 条目。 */
    private fun buildIco(images: List<Pair<Int, BufferedImage>>): ByteArray {
        val payloads = images.map { (size, image) -> size to (if (size >= PNG_SIZE) encodePng(image) else encodeBmp(image)) }
        val headerSize = 6 + 16 * payloads.size
        val out = ByteArrayOutputStream()
        out.writeLe16(0)
        out.writeLe16(1)
        out.writeLe16(payloads.size)
        var offset = headerSize
        for ((size, payload) in payloads) {
            out.write(if (size >= 256) 0 else size)
            out.write(if (size >= 256) 0 else size)
            out.write(0)
            out.write(0)
            out.writeLe16(1)
            out.writeLe16(32)
            out.writeLe32(payload.size)
            out.writeLe32(offset)
            offset += payload.size
        }
        for ((_, payload) in payloads) {
            out.write(payload)
        }
        return out.toByteArray()
    }

    /** 编码 32 位 BMP 条目：BITMAPINFOHEADER + 自下而上的 BGRA + 全零 AND 掩码。 */
    private fun encodeBmp(image: BufferedImage): ByteArray {
        val size = image.width
        val out = ByteArrayOutputStream()
        out.writeLe32(40)
        out.writeLe32(size)
        out.writeLe32(size * 2)
        out.writeLe16(1)
        out.writeLe16(32)
        out.writeLe32(0)
        out.writeLe32(0)
        out.writeLe32(0)
        out.writeLe32(0)
        out.writeLe32(0)
        out.writeLe32(0)
        for (y in size - 1 downTo 0) {
            for (x in 0 until size) {
                val argb = image.getRGB(x, y)
                out.write(argb and 0xFF)
                out.write((argb shr 8) and 0xFF)
                out.write((argb shr 16) and 0xFF)
                out.write((argb ushr 24) and 0xFF)
            }
        }
        val maskRowBytes = ((size + 31) / 32) * 4
        repeat(size) {
            repeat(maskRowBytes) { out.write(0) }
        }
        return out.toByteArray()
    }

    /** 编码 PNG 条目。 */
    private fun encodePng(image: BufferedImage): ByteArray {
        val out = ByteArrayOutputStream()
        javax.imageio.ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeLe16(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeLe32(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }
}
