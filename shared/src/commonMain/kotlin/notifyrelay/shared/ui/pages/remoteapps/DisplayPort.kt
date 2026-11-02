package notifyrelay.shared.ui.pages.remoteapps

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/** 显示器信息源。 */
interface DisplayPort {
    /** 当前可用显示器列表，随插拔更新。 */
    val displays: StateFlow<List<DisplayInfo>>

    /** 启动监听；重复调用无副作用。 */
    fun start()

    /** 停止监听。 */
    fun stop()
}

/** 由宿主提供实现。 */
val LocalDisplayPort = staticCompositionLocalOf<DisplayPort?> { null }
