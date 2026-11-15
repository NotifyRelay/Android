package notifyrelay.shared.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import notifyrelay.shared.ui.port.LocalToastPort
import kotlin.time.TimeSource

/**
 * 关于页的开发者模式激活入口。
 *
 * 连续点击版本信息 [DEVELOPER_MODE_CLICK_COUNT] 次即激活，两次点击间隔超过
 * [DEVELOPER_MODE_CLICK_WINDOW_MS] 则重新计数。已达激活状态后的点击不再计数、不再提示。
 * 两端共用这一份计数与提示文案，激活状态落到同一个偏好键。
 *
 * @return 点击回调；交给「版本信息」的 onClick
 */
@Composable
fun rememberDeveloperModeActivation(): () -> Unit {
    val toast = LocalToastPort.current
    val port = LocalSettingsPort.current
    var clickCount by remember { mutableIntStateOf(0) }
    var lastClickAt by remember { mutableLongStateOf(0L) }
    // 单调时钟：系统时间被校正时不影响连点窗口判定
    val clock = remember { TimeSource.Monotonic }

    return {
        val now = clock.markNow().elapsedNow().inWholeMilliseconds
        if (port?.isDeveloperModeEnabled() == true) {
            clickCount = 0
        } else {
            val continued = now - lastClickAt <= DEVELOPER_MODE_CLICK_WINDOW_MS
            clickCount = if (continued) clickCount + 1 else 1
            when {
                clickCount >= DEVELOPER_MODE_CLICK_COUNT -> {
                    port?.enableDeveloperMode()
                    toast.short("开发者模式已激活")
                    clickCount = 0
                }

                clickCount >= DEVELOPER_MODE_CLICK_COUNT - 2 -> {
                    toast.short("再点击 " + (DEVELOPER_MODE_CLICK_COUNT - clickCount) + " 次进入开发者模式")
                }
            }
        }
        lastClickAt = now
    }
}
