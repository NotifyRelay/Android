package github.xzynine.superislandui.floating.common

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import coil.compose.rememberAsyncImagePainter
import notifyrelay.base.util.image.ImageUtils
import notifyrelay.shared.util.DataUrlUtils

// Android 侧图片加载实现：data URL 交给 :base 的位图解码（与通知路径同一实现），其余 URL 交由 Coil 加载。
@Composable
actual fun rememberSuperIslandImagePainter(
    url: String?,
    picMap: Map<String, String>?,
    iconKey: String?,
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

    // data URL 直接解码为位图，与通知路径的取图口径一致
    if (DataUrlUtils.isDataUrl(resolvedUrl)) {
        val context = LocalContext.current
        val bitmap by produceState<Bitmap?>(initialValue = null, key1 = resolvedUrl) {
            value = ImageUtils.loadBitmap(context, resolvedUrl)
        }
        return bitmap?.let { BitmapPainter(it.asImageBitmap()) }
    }

    // 非 data URL 仍然使用 Coil
    return rememberAsyncImagePainter(model = resolvedUrl)
}
