package notifyrelay.shared.ui.screen

import androidx.compose.runtime.staticCompositionLocalOf

/** 设置页所需的平台能力。 */
interface SettingsPort {
    /** 开发者模式是否已激活。 */
    fun isDeveloperModeEnabled(): Boolean

    /** 激活开发者模式；已激活时为幂等。 */
    fun enableDeveloperMode()
}

/** 由宿主提供实现。 */
val LocalSettingsPort = staticCompositionLocalOf<SettingsPort?> { null }

/** 开发者模式偏好键：两端共用同一 Storage 键。 */
const val KEY_DEVELOPER_MODE_ENABLED = "developer_mode_enabled"

/** 激活开发者模式所需的连续点击数。 */
const val DEVELOPER_MODE_CLICK_COUNT = 5

/** 连续点击的间隔上限（毫秒）：超过则重新计数。 */
const val DEVELOPER_MODE_CLICK_WINDOW_MS = 3_000L
