package notifyrelay.desktop.overlay

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinUser.MONITORINFO
import com.sun.jna.platform.win32.WinUser.SWP_NOACTIVATE
import com.sun.jna.platform.win32.WinUser.SWP_NOMOVE
import com.sun.jna.platform.win32.WinUser.SWP_NOOWNERZORDER
import com.sun.jna.platform.win32.WinUser.SWP_NOSIZE
import com.sun.jna.platform.win32.WinUser.SWP_SHOWWINDOW
import com.sun.jna.platform.win32.WinUser.SW_SHOWNOACTIVATE
import com.sun.jna.platform.win32.WinUser.WS_MINIMIZE
import notifyrelay.base.log.Logger
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Window
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/**
 * 叠加层 Win32 原生能力：窗口样式、置顶强化、点击穿透、前台全屏检测。
 *
 * 实例化绑定一个叠加层窗口，守护线程周期校准置顶；原生操作失败后经 [nativeAvailable] 降级，
 * 避免永久重试。
 * - 用 JNA 平台库 `User32.INSTANCE`，不自定义 StdCallLibrary；
 * - [reinforceTopmost] 含 peer 变化检测、最小化恢复、调用后验证；
 * - [clickThrough] 可切换，始终保留 LAYERED；
 * - [foregroundIsFullscreen] 同显示器全屏检测。
 *
 * 窗口几何由调用方随内容测量结果设置（见 [moveTo]），本类不改窗口位置与尺寸。
 */
internal class DesktopWindowsOverlay(
    private val report: (Throwable) -> Unit = { Logger.w(TAG, "叠加层原生操作失败", it) },
) : AutoCloseable {
    private val user32 = if (Platform.isWindows()) User32.INSTANCE else null

    /** 是否支持点击穿透（仅 Windows）。 */
    val supportsClickThrough: Boolean get() = user32 != null

    /** 原生操作是否可用；失败后置 false 并停用后续原生调用。 */
    @Volatile
    private var nativeAvailable = true

    // ======== 置顶强化状态（仅守护线程访问）========
    private var lastTopmostPeer: HWND? = null
    private var nextTopmostRefresh = 0L
    private var peerGeneration = 0L

    // ======== 帧心跳 ========
    private val lastFrameAt = AtomicLong(0L)

    // ======== 交互区域（点击穿透切换）========

    /** 可交互区域（AWT 逻辑像素，窗口原点为屏幕原点）。 */
    private val interactiveRegions = AtomicReference<Array<Rectangle>>(emptyArray())

    /** 当前点击穿透态，避免重复 SetWindowLong。 */
    @Volatile
    private var clickThroughActive = true

    @Volatile
    private var running = false
    private var worker: Thread? = null
    private var boundWindow: Window? = null

    /** 记录一帧渲染完成，供卡死判定。 */
    fun onFrame() {
        lastFrameAt.set(System.currentTimeMillis())
    }

    /**
     * 更新可交互区域。
     *
     * 区域以窗口坐标表示；守护线程据此轮询鼠标位置，仅在鼠标位于某区域内时关闭点击穿透。
     */
    fun setInteractiveRegions(regions: List<Rectangle>) {
        interactiveRegions.set(regions.toTypedArray())
    }

    /** 当前可交互区域快照；供区域调试绘制。 */
    fun interactiveRegionsSnapshot(): List<Rectangle> = interactiveRegions.get().toList()

    /**
     * 把窗口移动到目标矩形。
     *
     * 由渲染侧按内容实测尺寸给出的屏幕坐标调用；尺寸与位置一次性提交，
     * 与置顶强化共用 EDT 投递，避免与 Skiko 渲染线程并发。
     */
    fun moveTo(
        window: Window,
        bounds: Rectangle,
    ) {
        if (window.bounds == bounds) return
        SwingUtilities.invokeLater {
            runCatching { window.setBounds(bounds) }
                .onFailure { report(it) }
        }
    }

    /**
     * 应用叠加层窗口样式：点击穿透、不抢焦点、不进任务栏。
     *
     * @return 是否成功应用
     */
    fun applyOverlayStyle(window: Window): Boolean {
        val api = user32 ?: return false
        if (!window.isDisplayable) return false
        val hwnd = existingHandle(window, api) ?: return false
        return runCatching {
            val current = api.GetWindowLong(hwnd, GWL_EXSTYLE)
            val target =
                current or
                    WS_EX_LAYERED or
                    WS_EX_TRANSPARENT or
                    WS_EX_NOACTIVATE or
                    WS_EX_TOOLWINDOW
            api.SetWindowLong(hwnd, GWL_EXSTYLE, target)
            true
        }.getOrElse {
            Logger.w(TAG, "应用叠加层窗口样式失败", it)
            false
        }
    }

    /**
     * 切换点击穿透。
     *
     * 始终保留 LAYERED（AWT 逐像素透明需要）；仅切 TRANSPARENT。
     */
    fun clickThrough(
        window: Window,
        enabled: Boolean,
    ) {
        val api = user32 ?: return
        if (!window.isDisplayable) return
        val hwnd = existingHandle(window, api) ?: return
        runCatching {
            val old = api.GetWindowLong(hwnd, GWL_EXSTYLE)
            val next = if (enabled) old or WS_EX_TRANSPARENT or WS_EX_LAYERED else old and WS_EX_TRANSPARENT.inv()
            if (next != old) {
                Native.setLastError(0)
                api.SetWindowLong(hwnd, GWL_EXSTYLE, next)
            }
        }.onFailure {
            nativeAvailable = false
            report(it)
        }
    }

    /**
     * 强化置顶：peer 变化检测、最小化恢复、调用后验证。
     *
     * @param refreshIntervalNs 节流间隔（纳秒）；未到期且状态未变时跳过
     * @return 是否成功维持置顶
     */
    fun reinforceTopmost(
        window: Window,
        refreshIntervalNs: Long,
    ): Boolean {
        val api = user32 ?: return true
        if (!window.isDisplayable || !window.isVisible) return false
        val hwnd = existingHandle(window, api) ?: return false
        val now = System.nanoTime()
        val peerChanged = hwnd != lastTopmostPeer
        if (peerChanged) {
            peerGeneration++
            lastTopmostPeer = hwnd
        }
        val wasTopmost = api.GetWindowLong(hwnd, GWL_EXSTYLE) and WS_EX_TOPMOST != 0
        val wasVisible = api.IsWindowVisible(hwnd)
        val wasMinimized = api.GetWindowLong(hwnd, GWL_STYLE) and WS_MINIMIZE != 0
        if (!peerChanged && wasTopmost && wasVisible && !wasMinimized && now < nextTopmostRefresh) return true

        if (wasMinimized) api.ShowWindow(hwnd, SW_SHOWNOACTIVATE)
        val flags =
            SWP_NOSIZE or SWP_NOMOVE or SWP_NOACTIVATE or SWP_NOOWNERZORDER or
                if (!wasVisible || wasMinimized) SWP_SHOWWINDOW else 0
        Native.setLastError(0)
        val requested = api.SetWindowPos(hwnd, HWND_TOPMOST, 0, 0, 0, 0, flags)
        val exists = api.IsWindow(hwnd)
        val isTopmost = exists && api.GetWindowLong(hwnd, GWL_EXSTYLE) and WS_EX_TOPMOST != 0
        val isVisible = exists && api.IsWindowVisible(hwnd)
        val isMinimized = exists && api.GetWindowLong(hwnd, GWL_STYLE) and WS_MINIMIZE != 0
        if (!exists) {
            lastTopmostPeer = null
            return false
        }
        if (!requested || !isTopmost || !isVisible || isMinimized) {
            nextTopmostRefresh = 0L
            return false
        }
        nextTopmostRefresh = now + refreshIntervalNs
        return true
    }

    /**
     * 前台窗口是否在同一显示器上全屏。
     *
     * 排除桌面壳层（Progman / WorkerW / Shell_TrayWnd）。
     */
    fun foregroundIsFullscreen(window: Window): Boolean {
        val api = user32 ?: return false
        if (!window.isDisplayable) return false
        val foreground = api.GetForegroundWindow() ?: return false
        if (foreground == handle(window)) return false
        val className = CharArray(256)
        api.GetClassName(foreground, className, className.size)
        if (Native.toString(className) in SHELL_CLASSES) return false
        val monitor = api.MonitorFromWindow(foreground, MONITOR_DEFAULTTONEAREST) ?: return false
        if (monitor != api.MonitorFromWindow(handle(window), MONITOR_DEFAULTTONEAREST)) return false
        val info = MONITORINFO()
        if (!api.GetMonitorInfo(monitor, info).booleanValue()) return false
        val rect = RECT()
        if (!api.GetWindowRect(foreground, rect)) return false
        val screen = info.rcMonitor
        return rect.left <= screen.left &&
            rect.top <= screen.top &&
            rect.right >= screen.right &&
            rect.bottom >= screen.bottom
    }

    /**
     * 开始监控：守护线程周期强化置顶。
     *
     * [interactiveMode] 开启时，鼠标位于可交互区域内则关闭点击穿透，离开则恢复；
     * 关闭时保持全程点击穿透。
     *
     * 帧心跳超时视为渲染卡死，清除置顶以免遮挡系统操作。
     */
    fun start(
        window: Window,
        interactiveMode: Boolean = false,
    ) {
        if (!Platform.isWindows()) return
        stop()
        boundWindow = window
        running = true
        clickThroughActive = true
        lastFrameAt.set(System.currentTimeMillis())
        Logger.i(TAG, "叠加层置顶监控已启动 interactive=" + interactiveMode)
        worker =
            thread(isDaemon = true, name = "desktop-overlay-topmost") {
                while (running) {
                    runCatching { Thread.sleep(POLL_INTERVAL_MS) }
                    if (!running) break
                    val w = boundWindow ?: break
                    val api = user32 ?: break
                    val hwnd = existingHandle(w, api)
                    if (hwnd == null) {
                        Logger.w(TAG, "叠加层窗口句柄失效，停止监控")
                        break
                    }
                    if (interactiveMode && nativeAvailable) {
                        syncClickThrough(w, api, hwnd)
                    }
                    val silentMs = System.currentTimeMillis() - lastFrameAt.get()
                    if (silentMs > HEARTBEAT_TIMEOUT_MS) {
                        clearTopmost(api, hwnd)
                        Logger.e(TAG, "渲染心跳超时 " + silentMs + "ms，已清除叠加层置顶")
                        continue
                    }
                    if (nativeAvailable) {
                        try {
                            reinforceTopmost(w, TOPMOST_REFRESH_NS)
                        } catch (e: Throwable) {
                            nativeAvailable = false
                            report(e)
                        }
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

    override fun close() {
        stop()
        boundWindow = null
    }

    // ======== 内部 ========

    private fun handle(window: Window) = HWND(Native.getWindowPointer(window))

    private fun existingHandle(
        window: Window,
        api: User32,
    ): HWND? {
        val pointer = runCatching { Native.getWindowPointer(window) }.getOrNull() ?: return null
        if (Pointer.nativeValue(pointer) == 0L) return null
        val hwnd = HWND(pointer)
        return hwnd.takeIf { api.IsWindow(it) }
    }

    /**
     * 按鼠标位置切换点击穿透：位于可交互区域内则关闭穿透，离开则恢复。
     *
     * 仅在状态变化时投递 SetWindowLong 到 EDT，避免与 Skiko 渲染线程并发导致窗口过程回调崩溃。
     * 区域以窗口坐标登记，故先把指针换算到窗口坐标系。
     */
    private fun syncClickThrough(
        window: Window,
        api: User32,
        hwnd: HWND,
    ) {
        val regions = interactiveRegions.get()
        val pointer = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull() ?: return
        val origin = window.location
        val local = java.awt.Point(pointer.x - origin.x, pointer.y - origin.y)
        val overContent = regions.any { it.contains(local) }
        val wantClickThrough = !overContent
        if (wantClickThrough == clickThroughActive) return
        clickThroughActive = wantClickThrough
        SwingUtilities.invokeLater {
            runCatching {
                val old = api.GetWindowLong(hwnd, GWL_EXSTYLE)
                val next =
                    if (wantClickThrough) {
                        old or WS_EX_TRANSPARENT or WS_EX_LAYERED
                    } else {
                        old and WS_EX_TRANSPARENT.inv()
                    }
                api.SetWindowLong(hwnd, GWL_EXSTYLE, next)
            }.onFailure {
                nativeAvailable = false
                report(it)
            }
        }
    }

    /** 清除 TOPMOST 扩展样式；不依赖窗口消息泵，渲染卡死时同样生效。 */
    private fun clearTopmost(
        api: User32,
        hwnd: HWND,
    ) {
        runCatching {
            val exStyle = api.GetWindowLong(hwnd, GWL_EXSTYLE)
            api.SetWindowLong(hwnd, GWL_EXSTYLE, exStyle and WS_EX_TOPMOST.inv())
        }
    }

    companion object {
        private const val TAG = "DesktopOverlayWin"

        private const val GWL_EXSTYLE = -20
        private const val GWL_STYLE = -16

        private const val WS_EX_TOPMOST = 0x00000008
        private const val WS_EX_LAYERED = 0x00080000
        private const val WS_EX_TRANSPARENT = 0x00000020
        private const val WS_EX_NOACTIVATE = 0x08000000
        private const val WS_EX_TOOLWINDOW = 0x00000080

        private const val MONITOR_DEFAULTTONEAREST = 2

        // HWND_TOPMOST：Int 重载在 x64 上零扩展 -1 会产生非法 HWND，故用 Pointer 构造
        private val HWND_TOPMOST = HWND(Pointer.createConstant(-1L))

        private val SHELL_CLASSES = setOf("Progman", "WorkerW", "Shell_TrayWnd")

        /** 巡检间隔。 */
        private const val POLL_INTERVAL_MS = 250L

        /** 置顶强化节流间隔（纳秒）：约每秒一次无条件重断言。 */
        private const val TOPMOST_REFRESH_NS = 1_000_000_000L

        /** 帧心跳超时阈值。 */
        private const val HEARTBEAT_TIMEOUT_MS = 10_000L
    }
}
