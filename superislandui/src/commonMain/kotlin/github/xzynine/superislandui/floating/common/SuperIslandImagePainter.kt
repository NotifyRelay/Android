package github.xzynine.superislandui.floating.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalInspectionMode
import notifyrelay.base.util.DataUrlUtils

/**
 * 加载超级岛图片为 [Painter]。
 *
 * data URL 用 Compose 的解码器直接解出位图；非 data URL 在本模块无法取网络字节，返回 null
 * （调用方按占位处理）。
 *
 * @param url 图片 URL；可为 data URL。
 * @param picMap 图标键值表，非空时按 [iconKey] 取值。
 * @param iconKey 图标键，非空时优先于 [url]。
 * @return 图片 [Painter]；无可用 URL 或预览模式下返回 null。
 */
@Composable
fun rememberSuperIslandImagePainter(
    url: String?,
    picMap: Map<String, String>? = null,
    iconKey: String? = null,
): Painter? {
    // 预览模式下返回 null，由调用方处理占位符
    if (LocalInspectionMode.current) return null

    val resolvedUrl =
        remember(url, picMap, iconKey) {
            if (!iconKey.isNullOrEmpty() && picMap != null) {
                picMap[iconKey]
            } else {
                url
            }
        }
    if (resolvedUrl.isNullOrEmpty()) return null

    val bitmap by
        produceState<ImageBitmap?>(initialValue = null, key1 = resolvedUrl) {
            value =
                if (DataUrlUtils.isDataUrl(resolvedUrl)) {
                    DataUrlUtils.cleanDataUrl(resolvedUrl)?.let { decodeDataUrlPayload(it) }
                } else {
                    null
                }
        }
    return bitmap?.let { BitmapPainter(it) }
}

/** 解码 data URL 负载：base64 走标准解码，其余按 UTF-8 字节。 */
private fun decodeDataUrlPayload(cleaned: String): ImageBitmap? {
    val comma = cleaned.indexOf(',')
    if (comma <= 0) return null
    val meta = cleaned.substring(0, comma)
    val raw = cleaned.substring(comma + 1)
    val bytes =
        runCatching {
            if (meta.contains("base64", ignoreCase = true)) {
                kotlin.io.encoding.Base64
                    .decode(raw)
            } else {
                raw.toByteArray(Charsets.UTF_8)
            }
        }.getOrNull() ?: return null
    return runCatching { bytes.decodeToImageBitmap() }.getOrNull()
}
