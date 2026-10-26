package notifyrelay.shared.ui.port

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/** 轻量提示端口。 */
interface ToastPort {
    fun short(message: String)

    fun long(message: String)
}

/** 剪贴板写入端口。 */
interface ClipboardPort {
    fun copyText(
        label: String,
        text: String,
    ): Boolean
}

/** 界面跳转端口（以 action 启动系统界面）。 */
interface IntentLaunchPort {
    fun startAction(
        action: String,
        data: String? = null,
        addNewTaskFlag: Boolean = false,
    )
}

/** 主题设置读写端口。 */
interface ThemePort {
    /** 当前主题基准索引（0 跟随系统 / 1 浅色 / 2 深色）。 */
    fun currentIndex(): Int

    fun setIndex(index: Int)

    fun addListener(listener: (Int) -> Unit)

    fun removeListener(listener: (Int) -> Unit)
}

/** 主题基准索引取值。 */
object ThemeBaseIndex {
    const val FOLLOW_SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
}

/** 系统栏颜色端口。 */
interface SystemBarPort {
    fun setStatusBarColor(
        color: Int,
        animate: Boolean = false,
    )

    fun setNavigationBarColor(
        color: Int,
        animate: Boolean = false,
    )
}

/** 位图转换端口（Drawable → 平台位图）。 */
interface BitmapPort {
    /** 把平台 Drawable 转为平台位图；intrinsic 非正数时使用兜底边长。 */
    fun drawableToBitmap(
        drawable: Any,
        fallbackSize: Int = 96,
    ): Any

    /** 解码图片字节为位图；失败返回 null。 */
    fun decodeByteArray(bytes: ByteArray): ImageBitmap?
}

/** 默认值均为不可用实现，宿主须在启动时覆盖。 */
val LocalToastPort =
    staticCompositionLocalOf<ToastPort> {
        error("LocalToastPort 未提供")
    }

val LocalClipboardPort =
    staticCompositionLocalOf<ClipboardPort> {
        error("LocalClipboardPort 未提供")
    }

val LocalIntentLaunchPort =
    staticCompositionLocalOf<IntentLaunchPort> {
        error("LocalIntentLaunchPort 未提供")
    }

val LocalThemePort =
    staticCompositionLocalOf<ThemePort> {
        error("LocalThemePort 未提供")
    }

val LocalSystemBarPort =
    staticCompositionLocalOf<SystemBarPort> {
        error("LocalSystemBarPort 未提供")
    }

val LocalBitmapPort =
    staticCompositionLocalOf<BitmapPort> {
        error("LocalBitmapPort 未提供")
    }
