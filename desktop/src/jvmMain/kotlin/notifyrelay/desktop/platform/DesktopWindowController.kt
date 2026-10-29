package notifyrelay.desktop.platform

import notifyrelay.base.log.Logger
import java.awt.Image
import java.awt.Point
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

/**
 * 桌面端窗口与托盘控制。
 *
 * 关闭窗口时隐藏到托盘并保持进程运行；托盘左键显示窗口，右键请求弹出菜单（由宿主以 Compose 弹层实现）。
 * 系统托盘不可用时关闭窗口即退出。
 */
class DesktopWindowController(
    private val exitApplication: () -> Unit,
) {
    private var window: Window? = null
    private var trayIcon: TrayIcon? = null
    private var exiting = false

    /** 右键托盘时回调，参数为屏幕坐标。 */
    var onMenuRequested: ((Point) -> Unit)? = null

    /** 托盘是否可用。 */
    val trayAvailable: Boolean
        get() = trayIcon != null

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
                .toLong()
        }.getOrElse {
            Logger.w(TAG, "取窗口句柄失败: " + it.message)
            0L
        }
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

        val tray =
            TrayIcon(icon, tooltip).apply {
                isImageAutoSize = true
                addActionListener { dispatch { showWindow() } }
                addMouseListener(
                    object : MouseAdapter() {
                        override fun mouseReleased(event: MouseEvent) {
                            when (event.button) {
                                MouseEvent.BUTTON1 -> dispatch { showWindow() }
                                MouseEvent.BUTTON3 -> {
                                    val point = Point(event.x, event.y)
                                    dispatch { onMenuRequested?.invoke(point) }
                                }
                            }
                        }
                    },
                )
            }
        val installed =
            runCatching {
                SystemTray.getSystemTray().add(tray)
                true
            }.getOrElse {
                Logger.w(TAG, "托盘安装失败: " + it.message)
                false
            }
        if (!installed) return false
        trayIcon = tray
        Logger.i(TAG, "托盘已安装")
        return true
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

    /** AWT 回调可能来自系统事件线程，统一投递到 Swing 事件线程。 */
    private fun dispatch(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) action() else SwingUtilities.invokeLater(action)
    }

    private companion object {
        const val TAG = "DesktopWindow"
    }
}
