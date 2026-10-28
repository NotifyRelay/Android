package notifyrelay.desktop.platform

import notifyrelay.base.log.Logger
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.Window
import javax.swing.SwingUtilities

/**
 * 桌面端窗口与托盘控制。
 *
 * 关闭窗口时隐藏到托盘并保持进程运行；托盘菜单提供显示或隐藏窗口与退出。
 * 系统托盘不可用时关闭窗口即退出。
 */
class DesktopWindowController(
    private val exitApplication: () -> Unit,
) {
    private var window: Window? = null
    private var trayIcon: TrayIcon? = null
    private var exiting = false

    /** 托盘是否可用。 */
    val trayAvailable: Boolean
        get() = trayIcon != null

    /** 绑定主窗口。 */
    fun bindWindow(window: Window) {
        this.window = window
    }

    /**
     * 安装托盘图标。
     *
     * @param icon 托盘图标，为空时为无托盘模式
     * @param tooltip 悬浮提示
     * @return 是否安装成功
     */
    fun installTray(
        icon: Image?,
        tooltip: String,
    ): Boolean {
        if (icon == null || !SystemTray.isSupported()) return false
        if (trayIcon != null) return true

        val menu =
            PopupMenu().apply {
                add(
                    MenuItem("显示或隐藏窗口").apply {
                        addActionListener { dispatch { toggleWindow() } }
                    },
                )
                addSeparator()
                add(
                    MenuItem("退出").apply {
                        addActionListener { dispatch { exit() } }
                    },
                )
            }

        val tray =
            TrayIcon(icon, tooltip, menu).apply {
                isImageAutoSize = true
                addActionListener { dispatch { showWindow() } }
            }
        return runCatching {
            SystemTray.getSystemTray().add(tray)
            trayIcon = tray
            Logger.i(TAG, "托盘已安装")
            true
        }.getOrElse {
            Logger.w(TAG, "托盘安装失败: " + it.message)
            false
        }
    }

    /** 关闭请求：托盘可用时隐藏窗口，否则退出。 */
    fun onCloseRequest() {
        if (exiting) return
        if (trayAvailable) hideWindow() else exit()
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

    /** 退出应用并卸载托盘。 */
    fun exit() {
        exiting = true
        disposeTray()
        exitApplication()
    }

    /** 卸载托盘图标。 */
    fun disposeTray() {
        val icon = trayIcon ?: return
        trayIcon = null
        runCatching { SystemTray.getSystemTray().remove(icon) }
    }

    /** 托盘回调可能来自 AWT 事件线程，统一投递到 Swing 事件线程后再操作窗口。 */
    private fun dispatch(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) action() else SwingUtilities.invokeLater(action)
    }

    private companion object {
        const val TAG = "DesktopWindow"
    }
}
