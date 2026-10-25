package notifyrelay.base.util

import android.view.Window
import java.lang.reflect.Method

/**
 * 系统栏工具类
 *
 * 纯 Android 平台工具：仅依赖 [Window] 与反射，不涉及 Compose / Context / 业务模型 / 资源，
 * 故由 `:app` 的 `ui/common` 下沉至 `:base`，供各模块复用。
 */
object SystemBarUtils {
    private val setStatusBarColor2: Method? by lazy {
        try {
            Window::class.java.getMethod("setStatusBarColor", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        } catch (_: Throwable) {
            null
        }
    }

    private val setStatusBarColor1: Method? by lazy {
        try {
            Window::class.java.getMethod("setStatusBarColor", Int::class.javaPrimitiveType)
        } catch (_: Throwable) {
            null
        }
    }

    private val setNavigationBarColor2: Method? by lazy {
        try {
            Window::class.java.getMethod("setNavigationBarColor", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        } catch (_: Throwable) {
            null
        }
    }

    private val setNavigationBarColor1: Method? by lazy {
        try {
            Window::class.java.getMethod("setNavigationBarColor", Int::class.javaPrimitiveType)
        } catch (_: Throwable) {
            null
        }
    }

    fun setStatusBarColor(
        window: Window,
        color: Int,
        animate: Boolean = false,
    ) {
        try {
            setStatusBarColor2?.invoke(window, color, animate) ?: setStatusBarColor1?.invoke(window, color)
        } catch (_: Throwable) {
            // best-effort: ignore
        }
    }

    fun setNavigationBarColor(
        window: Window,
        color: Int,
        animate: Boolean = false,
    ) {
        try {
            setNavigationBarColor2?.invoke(window, color, animate) ?: setNavigationBarColor1?.invoke(window, color)
        } catch (_: Throwable) {
            // best-effort: ignore
        }
    }
}
