package notifyrelay.desktop.platform

import androidx.compose.ui.graphics.ImageBitmap
import com.xzyht.notifyrelay.nativecore.device.appsync.IconCodecPort
import notifyrelay.base.util.decodeImageBitmap

/**
 * 图标编解码端口（桌面）。
 *
 * 本机应用不列出，故 [localIconBytes] 恒为 null；
 * 位图解码统一走公共 [decodeImageBitmap]。
 */
object DesktopIconCodecPort : IconCodecPort {
    override fun decodePng(bytes: ByteArray): Any? = decodeImageBitmap(bytes)

    override fun encodePng(platformBitmap: Any): ByteArray? = null

    override suspend fun localIconBytes(packageName: String): ByteArray? = null

    /** 把 PNG 字节解码为位图，供端口实现复用。 */
    fun decodeToBitmap(bytes: ByteArray): ImageBitmap? = decodeImageBitmap(bytes)
}
