package notifyrelay.desktop.overlay

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.win32.StdCallLibrary
import notifyrelay.base.log.Logger
import java.awt.Toolkit
import java.awt.Window
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/**
 * 叠加层置顶与几何监控。
 *
 * 覆盖层窗口的 TOPMOST 标志与 z-order 会被系统或其他应用重置（全屏程序退出、显示模式切换等），
 * 这里按「丢标志后立即恢复 + 周期无条件重断言」持续校准，并跟随显示器尺寸同步窗口大小；
 * 帧心跳超时视为渲染卡死，清除置顶以免遮挡系统操作。
 */
internal object DesktopOverlayTopmostMonitor {
    private const val TAG = "DesktopOverlayTop"

    private const val GWL_EXSTYLE = -20
    private const val WS_EX_TOPMOST = 0x00000008
    private const val HWND_TOPMOST = -1L
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOACTIVATE = 0x0010

    /** 巡检间隔。 */
    private const val POLL_INTERVAL_MS = 250L

    /** 无条件重断言的巡检次数间隔（约每秒一次）。 */
    private const val REASSERT_EVERY_TICKS = 4

    /** 置顶丢失后的补断言间隔，覆盖窗口管理器随后再次改写 z-order 的场景。 */
    private const val DELAYED_REASSERT_MS = 150L

    /** 帧心跳超时阈值。 */
    private const val HEARTBEAT_TIMEOUT_MS = 10_000L

    private interface User32 : StdCallLibrary {
        fun GetWindowLongPtrW(
            hWnd: Long,
            nIndex: Int,
        ): Long

        fun SetWindowLongPtrW(
            hWnd: Long,
            nIndex: Int,
            value: Long,
        ): Long

        fun SetWindowPos(
            hWnd: Long,
            insertAfter: Long,
            x: Int,
            y: Int,
            cx: Int,
            cy: Int,
            flags: Int,
        ): Boolean

        fun IsWindow(hWnd: Long): Boolean
    }

    private val user32: User32? by lazy {
        if (!Platform.isWindows()) {
            null
        } else {
            runCatching { Native.load("user32", User32::class.java) }.getOrNull()
        }
    }

    private val lastFrameAt = AtomicLong(0L)

    @Volatile
    private var running = false

    private var worker: Thread? = null

    /** 记录一帧渲染完成，供卡死判定。 */
    fun onFrame() {
        lastFrameAt.set(System.currentTimeMillis())
    }

    /**
     * 开始监控叠加层窗口。
     *
     * @param window 叠加层窗口
     */
    fun start(window: Window) {
        if (!Platform.isWindows()) return
        val hwnd =
            runCatching { Native.getWindowID(window) }.getOrElse {
                Logger.w(TAG, "取叠加层窗口句柄失败，置顶监控未启动", it)
                return
            }
        val lib = user32 ?: return
        stop()
        running = true
        lastFrameAt.set(System.currentTimeMillis())
        Logger.i(TAG, "置顶与几何监控已启动")
        worker =
            thread(isDaemon = true, name = "desktop-overlay-topmost") {
                var tick = 0
                var topmostCleared = false
                while (running) {
                    runCatching { Thread.sleep(POLL_INTERVAL_MS) }
                    if (!running) break
                    val alive = runCatching { lib.IsWindow(hwnd) }.getOrDefault(false)
                    if (!alive) {
                        Logger.w(TAG, "叠加层窗口已失效，停止置顶监控")
                        break
                    }
                    syncBounds(window)
                    val silentMs = System.currentTimeMillis() - lastFrameAt.get()
                    if (silentMs > HEARTBEAT_TIMEOUT_MS) {
                        if (!topmostCleared) {
                            clearTopmost(lib, hwnd)
                            topmostCleared = true
                            Logger.e(TAG, "渲染心跳超时 " + silentMs + "ms，已清除叠加层置顶")
                        }
                        continue
                    }
                    topmostCleared = false
                    if (!isTopmost(lib, hwnd)) {
                        reassertTopmost(lib, hwnd)
                        runCatching { Thread.sleep(DELAYED_REASSERT_MS) }
                        reassertTopmost(lib, hwnd)
                        Logger.i(TAG, "叠加层置顶已恢复")
                    }
                    if (++tick % REASSERT_EVERY_TICKS == 0) {
                        reassertTopmost(lib, hwnd)
                    }
                }
            }
    }

    /** 停止监控。 */
    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
    }

    /** 跟随显示器尺寸同步叠加层窗口。 */
    private fun syncBounds(window: Window) {
        val screen = runCatching { Toolkit.getDefaultToolkit().screenSize }.getOrNull() ?: return
        val bounds = window.bounds
        if (bounds.x == 0 && bounds.y == 0 && bounds.width == screen.width && bounds.height == screen.height) {
            return
        }
        Logger.i(TAG, "显示器尺寸变化，同步叠加层窗口: " + screen.width + "x" + screen.height)
        SwingUtilities.invokeLater { window.setBounds(0, 0, screen.width, screen.height) }
    }

    /** 窗口是否仍带 TOPMOST 扩展样式。 */
    private fun isTopmost(
        lib: User32,
        hwnd: Long,
    ): Boolean {
        val exStyle = runCatching { lib.GetWindowLongPtrW(hwnd, GWL_EXSTYLE) }.getOrDefault(0L)
        return (exStyle and WS_EX_TOPMOST.toLong()) != 0L
    }

    /** 重断言 TOPMOST z-order；不移动、不缩放、不激活。 */
    private fun reassertTopmost(
        lib: User32,
        hwnd: Long,
    ) {
        runCatching {
            lib.SetWindowPos(
                hwnd,
                HWND_TOPMOST,
                0,
                0,
                0,
                0,
                SWP_NOMOVE or SWP_NOSIZE or SWP_NOACTIVATE,
            )
        }
    }

    /** 清除 TOPMOST 扩展样式；不依赖窗口消息泵，渲染卡死时同样生效。 */
    private fun clearTopmost(
        lib: User32,
        hwnd: Long,
    ) {
        runCatching {
            val exStyle = lib.GetWindowLongPtrW(hwnd, GWL_EXSTYLE)
            lib.SetWindowLongPtrW(hwnd, GWL_EXSTYLE, exStyle and WS_EX_TOPMOST.toLong().inv())
        }
    }
}
