package com.xzyht.notifyrelay.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 导航器类，管理导航栈和结果传递
 * Navigator class for managing navigation stack and result delivery
 */
class Navigator(initialKey: NavKey) {
    val backStack: SnapshotStateList<NavKey> = mutableStateListOf(initialKey)

    private val resultBus = mutableMapOf<String, MutableSharedFlow<Any>>()

    /**
     * 将新路由压入导航栈
     * Push a new route onto the navigation stack
     */
    fun push(key: NavKey) {
        backStack.add(key)
    }

    /**
     * 替换当前路由
     * Replace the current route
     */
    fun replace(key: NavKey) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = key
        } else {
            backStack.add(key)
        }
    }

    /**
     * 弹出当前路由
     * Pop the current route
     */
    fun pop() {
        backStack.removeLastOrNull()
    }

    /**
     * 弹出直到满足条件
     * Pop until the predicate is satisfied
     */
    fun popUntil(predicate: (NavKey) -> Boolean) {
        while (backStack.isNotEmpty() && !predicate(backStack.last())) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    /**
     * 导航并等待结果
     * Navigate for result
     */
    fun navigateForResult(route: Route, requestKey: String) {
        ensureChannel(requestKey)
        push(route)
    }

    /**
     * 设置结果并返回
     * Set result and pop
     */
    fun <T : Any> setResult(requestKey: String, value: T) {
        ensureChannel(requestKey).tryEmit(value)
        pop()
    }

    /**
     * 观察结果
     * Observe result
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> observeResult(requestKey: String): SharedFlow<T> {
        return ensureChannel(requestKey) as SharedFlow<T>
    }

    /**
     * 清除结果
     * Clear result
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearResult(requestKey: String) {
        ensureChannel(requestKey).resetReplayCache()
    }

    /**
     * 获取当前路由
     * Get current route
     */
    fun current(): NavKey? = backStack.lastOrNull()

    /**
     * 获取导航栈大小
     * Get back stack size
     */
    fun backStackSize(): Int = backStack.size

    private fun ensureChannel(key: String): MutableSharedFlow<Any> {
        return resultBus.getOrPut(key) { MutableSharedFlow(replay = 1, extraBufferCapacity = 0) }
    }

    companion object {
        val Saver: Saver<Navigator, Any> = listSaver(
            save = { navigator -> navigator.backStack.toList() },
            restore = { savedList ->
                val initialKey = savedList.firstOrNull() ?: Route.Main
                val navigator = Navigator(initialKey)
                navigator.backStack.clear()
                navigator.backStack.addAll(savedList)
                navigator
            }
        )
    }
}

/**
 * 记住导航器实例
 * Remember navigator instance
 */
@Composable
fun rememberNavigator(startRoute: NavKey): Navigator {
    return rememberSaveable(startRoute, saver = Navigator.Saver) {
        Navigator(startRoute)
    }
}

/**
 * 本地导航器提供者
 * Local navigator provider
 */
val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("LocalNavigator not provided")
}
