package notifyrelay.desktop.platform

import notifyrelay.shared.ui.pages.history.NotificationActionPort

/**
 * 桌面端通知交互实现。
 *
 * 桌面平台没有「启动 Android 应用」的概念，打开应用退化为提示；
 * 重发通知输出到控制台，后续接入系统通知时替换此处即可。
 */
class DesktopNotificationActionPort : NotificationActionPort {
    override fun openApp(packageName: String): String = "桌面端不支持启动应用：" + packageName

    override fun showToast(message: String) {
        println("[提示] " + message)
    }

    override fun resendAsHighPriority(
        title: String?,
        text: String?,
    ) {
        println("[重发通知] " + (title ?: "") + " - " + (text ?: ""))
    }
}
