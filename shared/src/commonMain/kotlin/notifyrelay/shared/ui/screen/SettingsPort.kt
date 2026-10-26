package notifyrelay.shared.ui.screen

import androidx.compose.runtime.staticCompositionLocalOf

/** 设置页所需的平台能力。 */
interface SettingsPort {
    /** 开发者模式是否已激活。 */
    fun isDeveloperModeEnabled(): Boolean

    /** 打开开发者选项页面。 */
    fun openDeveloperMode()
}

/** 由宿主提供实现。 */
val LocalSettingsPort = staticCompositionLocalOf<SettingsPort?> { null }
