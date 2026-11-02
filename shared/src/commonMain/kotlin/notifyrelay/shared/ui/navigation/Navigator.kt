package notifyrelay.shared.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.navBackStackOf

/** 返回栈的持久化编码，与 miuix-nav 的 NavBackStack Saver 口径一致（kotlinx.serialization JSON）。 */
private val navigatorJson = Json { ignoreUnknownKeys = true }

/** `Route` 是工程内唯一的 `NavKey` 实现。 */
private val routeListSerializer = ListSerializer(Route.serializer())

/**
 * 导航器类，管理导航栈和结果传递
 * Navigator class for managing navigation stack and result delivery
 */
class Navigator(
    initialKey: NavKey,
) {
    val backStack: NavBackStack = navBackStackOf(initialKey)

    private val controller = NavController(backStack)

    private val resultBus = mutableMapOf<String, MutableSharedFlow<Any>>()

    /**
     * 将新路由压入导航栈
     * Push a new route onto the navigation stack
     */
    fun push(key: NavKey) {
        controller.push(key)
    }

    /**
     * 替换当前路由
     * Replace the current route
     */
    fun replace(key: NavKey) {
        controller.replace(key)
    }

    /**
     * 弹出当前路由。
     *
     * 保留最后一项：导航栈为空会使 NavDisplay 抛 IllegalArgumentException，
     * 且根页面本身不应被弹出。
     */
    fun pop() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
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
    fun navigateForResult(
        route: Route,
        requestKey: String,
    ) {
        ensureChannel(requestKey)
        push(route)
    }

    /**
     * 设置结果并返回
     * Set result and pop
     */
    fun <T : Any> setResult(
        requestKey: String,
        value: T,
    ) {
        ensureChannel(requestKey).tryEmit(value)
        pop()
    }

    /**
     * 观察结果
     * Observe result
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> observeResult(requestKey: String): SharedFlow<T> = ensureChannel(requestKey) as SharedFlow<T>

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

    private fun ensureChannel(key: String): MutableSharedFlow<Any> = resultBus.getOrPut(key) { MutableSharedFlow(replay = 1, extraBufferCapacity = 0) }

    companion object {
        val Saver: Saver<Navigator, String> =
            Saver(
                save = { navigator -> navigatorJson.encodeToString(routeListSerializer, navigator.backStack.filterIsInstance<Route>()) },
                restore = { encoded ->
                    val savedList = navigatorJson.decodeFromString(routeListSerializer, encoded)
                    val initialKey = savedList.firstOrNull() ?: Route.Main
                    val navigator = Navigator(initialKey)
                    navigator.backStack.clear()
                    navigator.backStack.addAll(savedList)
                    navigator
                },
            )
    }
}

/**
 * 记住导航器实例
 * Remember navigator instance
 */
@Composable
fun rememberNavigator(startRoute: NavKey): Navigator =
    rememberSaveable(startRoute, saver = Navigator.Saver) {
        Navigator(startRoute)
    }

/**
 * 本地导航器提供者
 * Local navigator provider
 */
val LocalNavigator =
    staticCompositionLocalOf<Navigator> {
        error("LocalNavigator not provided")
    }
