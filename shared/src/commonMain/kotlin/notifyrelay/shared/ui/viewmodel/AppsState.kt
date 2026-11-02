package notifyrelay.shared.ui.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 应用列表页面的通用状态。
 *
 * @param T 列表元素类型。
 */
data class AppsState<T>(
    val apps: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
) {
    val isEmpty: Boolean
        get() = !isLoading && apps.isEmpty() && error == null
}

/**
 * 应用列表状态机：承载 [AppsState] 的读写与加载/搜索流程。
 *
 * @param T 列表元素类型。
 * @param K 加载键类型。
 * @param loader 加载完整应用列表，接收 [load] 传入的加载键。
 */
class AppsStateMachine<T, K>(
    private val loader: suspend (K) -> List<T>,
) {
    private val _state = MutableStateFlow(AppsState<T>())
    val state: StateFlow<AppsState<T>> = _state.asStateFlow()

    private var activeKey: K? = null

    suspend fun load(
        key: K,
        onError: ((Exception) -> Unit)? = null,
    ) {
        activeKey = key
        _state.update { it.copy(isLoading = true, error = null) }
        try {
            val apps = loader(key)
            if (activeKey != key) return
            _state.update { it.copy(apps = apps, isLoading = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (activeKey != key) return
            onError?.invoke(e)
            _state.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    fun searchApps(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun update(transform: (AppsState<T>) -> AppsState<T>) {
        _state.update(transform)
    }
}
