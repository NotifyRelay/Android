package notifyrelay.desktop.platform

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import notifyrelay.base.log.Logger

/**
 * 桌面端提示承载。
 *
 * 提示经 Miuix `Snackbar` 在主窗口底部展示：本类只做「入队 + 状态流」，
 * 渲染由 `Main.kt` 根 Scaffold 的 `SnackbarHost` 负责。
 *
 * 提示可能来自非界面线程（如 scrcpy 进程监控、剪贴板事件），故统一经通道投递，
 * 由界面侧收集后再调用 `showSnackbar`。
 */
object DesktopToastHost {
    private const val TAG = "DesktopToast"

    /** 提示时长（毫秒）：桌面端比移动端更短，避免遮挡内容。 */
    const val SHORT_MILLIS = 1_800L
    const val LONG_MILLIS = 3_500L

    /** 提示时长档位。 */
    enum class Duration {
        /** 短提示。 */
        SHORT,

        /** 长提示。 */
        LONG,
    }

    /** 一条待展示提示。 */
    data class Message(
        val text: String,
        val duration: Duration,
    )

    private val channel = Channel<Message>(capacity = Channel.BUFFERED)

    /** 提示流；界面侧收集后展示。 */
    val messages: Flow<Message> = channel.receiveAsFlow()

    /** 入队一条提示；任意线程可调用。 */
    fun show(
        text: String,
        duration: Duration,
    ) {
        if (text.isBlank()) return
        val queued = channel.trySend(Message(text, duration))
        if (!queued.isSuccess) {
            // 通道异常时至少留下日志，避免提示静默丢失
            Logger.w(TAG, "提示入队失败: " + text)
        }
    }
}
