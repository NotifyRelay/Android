package notifyrelay.desktop.platform

import notifyrelay.base.log.Logger
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO

/**
 * 系统媒体中心的封面来源。
 *
 * Windows SMTC 只接受 `file://`、本地路径或 `http(s)` 地址，不接受 `data:` URL；
 * 因此会话封面（base64 data URL）与应用图标都会先落成缓存文件再交给系统。
 *
 * - 有会话封面：使用会话封面
 * - 无会话封面：使用应用图标
 */
internal object DesktopMediaCover {
    private const val TAG = "DesktopMediaCover"

    /** 封面缓存目录，与应用数据目录同级。 */
    private val cacheDir: File
        get() = File(System.getProperty("user.home"), ".notifyrelay/media-cover").apply { mkdirs() }

    /** 应用图标缓存文件：首次使用时按 256 像素栅格化。 */
    @Volatile
    private var appIconFile: File? = null

    /**
     * 取封面地址。
     *
     * @param coverUrl 会话封面；可为 `data:` URL、`http(s)` 地址或本地路径
     * @return 可交给系统媒体中心的封面地址；两者都不可用时返回 null
     */
    fun resolve(coverUrl: String?): String? {
        if (!coverUrl.isNullOrBlank()) {
            when {
                coverUrl.startsWith("data:", ignoreCase = true) -> dataUrlToFile(coverUrl)?.let { return toFileUrl(it) }
                coverUrl.startsWith("http://", ignoreCase = true) ||
                    coverUrl.startsWith("https://", ignoreCase = true) -> return coverUrl
                coverUrl.startsWith("file:", ignoreCase = true) -> return coverUrl
                else -> File(coverUrl).takeIf { it.isFile }?.let { return toFileUrl(it) }
            }
        }
        return appIconFile()?.let { toFileUrl(it) }
    }

    /** 应用图标以 PNG 形式缓存，作为无会话封面时的兜底。 */
    private fun appIconFile(): File? {
        appIconFile?.let { if (it.isFile) return it }
        return synchronized(this) {
            appIconFile?.let { if (it.isFile) return it }
            val target = File(cacheDir, "app-icon.png")
            if (target.isFile && target.length() > 0) {
                appIconFile = target
                return target
            }
            val bytes =
                runCatching {
                    val svg = AppIcon.loadSvgBytes() ?: return null
                    val image = AppIcon.render(svg, APP_ICON_SIZE)
                    java.io.ByteArrayOutputStream().use { stream ->
                        ImageIO.write(image, "png", stream)
                        stream.toByteArray()
                    }
                }.onFailure { Logger.w(TAG, "生成应用图标封面失败", it) }.getOrNull() ?: return null
            runCatching { target.writeBytes(bytes) }.isSuccess
            appIconFile = target.takeIf { it.isFile }
            appIconFile
        }
    }

    /** `data:` URL 解码后落成缓存文件，按内容摘要去重。 */
    private fun dataUrlToFile(dataUrl: String): File? {
        val comma = dataUrl.indexOf(',')
        if (comma <= 0) return null
        val meta = dataUrl.substring(5, comma)
        if (!meta.contains("base64", ignoreCase = true)) return null
        val bytes =
            runCatching {
                java.util.Base64
                    .getDecoder()
                    .decode(dataUrl.substring(comma + 1))
            }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        val extension =
            when {
                bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
                bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
                bytes.size >= 4 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "webp"
                else -> "png"
            }
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
                .take(16)
        val target = File(cacheDir, digest + "." + extension)
        if (target.isFile && target.length() == bytes.size.toLong()) return target
        return runCatching {
            target.writeBytes(bytes)
            target
        }.getOrElse {
            Logger.w(TAG, "写入封面缓存失败", it)
            null
        }
    }

    /** 本地文件转 `file://` 地址。 */
    private fun toFileUrl(file: File): String = file.toURI().toString()

    /** 系统媒体中心封面边长。 */
    private const val APP_ICON_SIZE = 256
}
