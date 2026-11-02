package notifyrelay.shared.ui.pages.history

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 通知历史页所需的平台交互能力。
 *
 * 历史卡片的「打开应用」「提示失败」「以高优先级重发通知」都依赖平台 API，
 * 抽象为端口后页面本身可与平台无关。
 */
interface NotificationActionPort {
    /**
     * 打开指定包名的应用。
     *
     * @return 失败原因；成功返回 null
     */
    fun openApp(packageName: String): String?

    /** 显示短提示。 */
    fun showToast(message: String)

    /** 以高优先级重新发送一条通知（用于从历史恢复）。 */
    fun resendAsHighPriority(
        title: String?,
        text: String?,
    )
}

/** 由宿主提供实现。 */
val LocalNotificationActionPort = staticCompositionLocalOf<NotificationActionPort?> { null }
