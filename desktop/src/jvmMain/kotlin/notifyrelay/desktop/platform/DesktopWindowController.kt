package notifyrelay.desktop.platform

import notifyrelay.base.log.Logger
import java.awt.Window

/**
 * 桌面端窗口控制。
 *
 * 关闭窗口时隐藏到托盘并保持进程运行；托盘由 [DesktopTray] 通过原生托盘后端提供。
 */
class DesktopWindowController(
    private val exitApplication: () -> Unit,
) {
    private var window: Window? = null
    private var exiting = false

    /** 绑定主窗口。 */
    fun bindWindow(window: Window) {
        this.window = window
    }

    /**
     * 取主窗口的原生句柄（Windows 为 HWND，其余平台为 0）。
     *
     * AWT 不直接暴露 HWND；Compose Desktop 的窗口是 `JFrame`，
     * 经 JNA 的 `getWindowID` 取得 AWT 内部窗口 ID（即 HWND）。
     */
    fun nativeHandle(): Long {
        val current = window ?: return 0L
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return 0L
        return runCatching {
            com.sun.jna.Native
                .getWindowID(current)
        }.getOrElse {
            Logger.w(TAG, "取窗口句柄失败: " + it.message)
            0L
        }
    }

    /** 关闭请求：隐藏窗口到托盘。 */
    fun onCloseRequest() {
        if (exiting) return
        hideWindow()
    }

    /** 隐藏主窗口。 */
    fun hideWindow() {
        window?.let {
            it.isVisible = false
            Logger.i(TAG, "窗口已隐藏到托盘")
        }
    }

    /** 显示并前置主窗口。 */
    fun showWindow() {
        window?.let {
            it.isVisible = true
            it.toFront()
            it.requestFocus()
            Logger.i(TAG, "窗口已显示")
        }
    }

    /** 显示与隐藏互切。 */
    fun toggleWindow() {
        val current = window ?: return
        if (current.isVisible) hideWindow() else showWindow()
    }

    /** 退出应用。 */
    fun exit() {
        exiting = true
        exitApplication()
    }

    private companion object {
        const val TAG = "DesktopWindow"
    }
}
