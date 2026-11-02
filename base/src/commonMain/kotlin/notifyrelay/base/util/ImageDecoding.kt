package notifyrelay.base.util

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 图片字节解码入口（平台无关声明）。
 *
 * 解码后端由平台提供：Android 用 `BitmapFactory`，JVM 用 Skia；
 * 公共端各处（应用图标、超级岛图片、通知图标）统一经此解码为 [ImageBitmap]。
 *
 * @param bytes 图片字节（PNG / JPEG / WebP 等）。
 * @return 解码后的位图；字节为空或解码失败时返回 null。
 */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
