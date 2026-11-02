package notifyrelay.shared.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf

/** 主界面所需的平台能力。 */
interface MainScreenPort {
    /** 是否横屏。 */
    @Composable
    fun isLandscape(): Boolean

    /** 是否显示自启动提示横幅。 */
    fun showAutoStartBanner(): State<Boolean>

    /** 自启动提示文案。 */
    fun bannerMessage(): State<String?>

    /** 打开本应用的系统设置页。 */
    fun openAppSettings()

    /** 结束宿主界面。 */
    fun finish()

    /** 提示「再次返回以退出应用」。 */
    fun showExitHint()
}

/** 由宿主提供实现。 */
val LocalMainScreenPort = staticCompositionLocalOf<MainScreenPort?> { null }
