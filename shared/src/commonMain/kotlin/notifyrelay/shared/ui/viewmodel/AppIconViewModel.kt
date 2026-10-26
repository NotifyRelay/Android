package notifyrelay.shared.ui.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import notifyrelay.shared.feature.appslist.AppIconPort

/**
 * 应用图标承载（平台无关）。
 *
 * 图标数据经 [AppIconPort] 提供，本类不持有平台上下文。
 */
class AppIconViewModel(
    private val port: AppIconPort,
) : ViewModel() {
    private val cache = mutableMapOf<String, ImageBitmap?>()

    /** 图标更新信号。 */
    val iconUpdatesFlow get() = port.iconUpdates

    /** 取指定包名的图标；结果经 [onResult] 回调。 */
    fun loadIcon(
        packageName: String,
        onResult: (ImageBitmap?) -> Unit,
    ) {
        cache[packageName]?.let {
            onResult(it)
            return
        }
        viewModelScope.launch {
            val icon = runCatching { port.localIcon(packageName) }.getOrNull()
            cache[packageName] = icon
            onResult(icon)
        }
    }

    /** 取外部应用图标。 */
    fun externalIcon(
        packageName: String,
        onResult: (ImageBitmap?) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(runCatching { port.externalIcon(packageName) }.getOrNull())
        }
    }

    /** 取应用图标并在缺失时自动请求。 */
    fun iconWithAutoRequest(
        packageName: String,
        onResult: (ImageBitmap?) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(runCatching { port.iconWithAutoRequest(packageName) }.getOrNull())
        }
    }

    /** 取系统默认应用图标。 */
    fun defaultIcon(size: Int = 48): ImageBitmap? = runCatching { port.defaultIcon(size) }.getOrNull()
}
