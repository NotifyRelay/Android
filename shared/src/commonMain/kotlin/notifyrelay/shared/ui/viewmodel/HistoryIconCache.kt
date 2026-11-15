package notifyrelay.shared.ui.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import notifyrelay.domain.feature.notification.history.AppIconInfo

/**
 * 历史页共用的应用图标缓存。
 *
 * 通知历史与超级岛历史两个页面的图标加载完全相同：按包名缓存「应用名 + 图标」，
 * 缺什么补什么，同一包名在途时只发一次请求。差异只在图标来源（各自的端口），
 * 因此取图标的方法由调用方以 lambda 注入。
 *
 * @param scope 承载加载协程的作用域，通常是所属 ViewModel 的 viewModelScope。
 * @param loadIcons 按包名批量取图标。
 */
class HistoryIconCache(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val loadIcons: suspend (List<String>) -> Map<String, AppIconInfo>,
) {
    private val _icons = MutableStateFlow<Map<String, Pair<String, ImageBitmap?>>>(emptyMap())

    /** 包名到「应用名 + 图标」的映射。 */
    val icons: StateFlow<Map<String, Pair<String, ImageBitmap?>>> = _icons.asStateFlow()

    /** 在途请求的包名，避免同一包名重复发起。 */
    private val loading = mutableSetOf<String>()

    /** 使某个包名的缓存失效，随后按需重新加载。 */
    fun invalidate(packageName: String) {
        _icons.update { it - packageName }
    }

    /**
     * 补齐缺失的图标。
     *
     * 只请求「非空白、尚未缓存、且不在途」的包名；请求结束后无论成败都释放在途标记，
     * 使失败项能在下次调用时重试。
     *
     * @param packageNames 需要图标的包名。
     */
    fun preload(packageNames: List<String>) {
        val missing = packageNames.filter { it.isNotBlank() && !_icons.value.containsKey(it) && loading.add(it) }
        if (missing.isEmpty()) return
        scope.launch {
            try {
                val loaded = loadIcons(missing)
                _icons.update { cache ->
                    cache + loaded.mapValues { (_, info) -> info.appName to info.icon }
                }
            } finally {
                loading.removeAll(missing.toSet())
            }
        }
    }
}

/**
 * 订阅图标更新信号：失效对应包名并立即补回。
 *
 * 两个历史页都对同一个端口信号做同样的反应，故收敛在这里。
 *
 * @param updates 图标更新信号，元素为「包名 + 时间戳」。
 * @param cache 要刷新的缓存。
 */
fun ViewModel.observeIconUpdates(
    updates: kotlinx.coroutines.flow.Flow<Pair<String, Long>?>,
    cache: HistoryIconCache,
) {
    viewModelScope.launch {
        updates.collect { update ->
            update?.let { (packageName, _) ->
                cache.invalidate(packageName)
                cache.preload(listOf(packageName))
            }
        }
    }
}
