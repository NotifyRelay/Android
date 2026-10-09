package com.xzyht.notifyrelay.ui.viewmodel

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 应用列表页面的通用状态。
 *
 * 本地（`PackageManager`）与远程（`RemoteAppsCache.getRemoteAppsList`）两个数据源共用本状态，
 * 差异只体现在 [T] 与各自的加载实现上。
 *
 * @param T 列表元素类型，需提供包名与应用名供搜索匹配使用。
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
 * 由 ViewModel 以组合方式持有，各数据源只需提供 [loader] 实现，
 * 加载态与错误处理流程由此处单点维护。
 *
 * @param T 列表元素类型。
 * @param loader 加载完整应用列表；抛出的异常由本类统一捕获并写入 [AppsState.error]。
 */
internal class AppsStateMachine<T>(
    private val loader: suspend (Context) -> List<T>,
) {
    private val _state = MutableStateFlow(AppsState<T>())
    val state: StateFlow<AppsState<T>> = _state.asStateFlow()

    /**
     * 加载应用列表：置加载态、执行 [loader]、写回结果或错误。
     *
     * @param context Android 上下文，交给 [loader] 使用。
     * @param onError 捕获到异常时的附加处理（如记录日志），在写入 [AppsState.error] 之前调用。
     */
    suspend fun load(
        context: Context,
        onError: ((Exception) -> Unit)? = null,
    ) {
        _state.update { it.copy(isLoading = true, error = null) }
        try {
            val apps = loader(context)
            _state.update { it.copy(apps = apps, isLoading = false) }
        } catch (e: Exception) {
            onError?.invoke(e)
            _state.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    /**
     * 仅更新搜索词，列表过滤由消费方按 [AppsState.apps] 与搜索词自行完成。
     *
     * @param query 新的搜索关键字。
     */
    fun searchApps(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    /**
     * 以 [transform] 直接改写状态，供加载流程之外的局部更新（单项图标刷新、置顶态同步）使用。
     *
     * @param transform 以当前状态为输入返回新状态。
     */
    fun update(transform: (AppsState<T>) -> AppsState<T>) {
        _state.update(transform)
    }
}
