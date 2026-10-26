package notifyrelay.shared.ui.pages.remoteapps

import androidx.compose.runtime.staticCompositionLocalOf

/** 本机应用启动端口。 */
interface AppLaunchPort {
    /** 在指定显示器启动 [packageName]；无启动入口时无操作。 */
    fun launchLocalApp(
        packageName: String,
        displayId: Int,
    )
}

/** 由宿主提供实现。 */
val LocalAppLaunchPort = staticCompositionLocalOf<AppLaunchPort?> { null }
