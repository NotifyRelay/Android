package notifyrelay.shared.ui.common

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import notifyrelay.base.log.Logger

/**
 * 调试开关的公共持有处。
 *
 * 原先位于 `:app` 的 `DeveloperModeActivity` 伴生对象，被设置页与过滤链路共用；
 * 上移到公共端后 Android 与桌面共用同一份状态。
 *
 * 值由平台在启动时经 [load] 从偏好读出；写入方为开发者选项页。
 */
object DebugFlags {
    /** 浮窗/去重调试：超级岛浮窗说明显示调试信息、智能去重输出调试日志。 */
    val DEBUG_UI_ENABLED: MutableState<Boolean> = mutableStateOf(false)

    /** 是否输出被过滤通知的详细日志。 */
    val FILTERED_NOTIFICATION_LOG_ENABLED: MutableState<Boolean> = mutableStateOf(true)

    /** 从偏好装载初值；[read] 以（键, 默认值）读取布尔偏好。 */
    fun load(read: (String, Boolean) -> Boolean) {
        DEBUG_UI_ENABLED.value = read(KEY_DEBUG_UI_ENABLED, false)
        FILTERED_NOTIFICATION_LOG_ENABLED.value = read(KEY_FILTERED_NOTIFICATION_LOG_ENABLED, true)
        Logger.enableFilteredNotificationLog = FILTERED_NOTIFICATION_LOG_ENABLED.value
    }

    /** 写入调试 UI 开关并持久化。 */
    fun setDebugUiEnabled(
        enabled: Boolean,
        write: (String, Boolean) -> Unit,
    ) {
        DEBUG_UI_ENABLED.value = enabled
        write(KEY_DEBUG_UI_ENABLED, enabled)
    }

    /** 写入过滤日志开关、同步日志通道并持久化。 */
    fun setFilteredNotificationLogEnabled(
        enabled: Boolean,
        write: (String, Boolean) -> Unit,
    ) {
        FILTERED_NOTIFICATION_LOG_ENABLED.value = enabled
        Logger.enableFilteredNotificationLog = enabled
        write(KEY_FILTERED_NOTIFICATION_LOG_ENABLED, enabled)
    }

    /** 调试 UI 开关的偏好键。 */
    const val KEY_DEBUG_UI_ENABLED = "debug_ui_enabled"

    /** 过滤日志开关的偏好键。 */
    const val KEY_FILTERED_NOTIFICATION_LOG_ENABLED = "filtered_notification_log_enabled"
}
