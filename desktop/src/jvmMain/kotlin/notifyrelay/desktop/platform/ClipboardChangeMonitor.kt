package notifyrelay.desktop.platform

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import notifyrelay.base.log.Logger

/**
 * 系统剪贴板变更监听（事件驱动）。
 *
 * 与 [PowerStatusMonitor] 同为「JNA 调 Win32 + 系统回调」的方式，不做轮询。
 *
 * Win32 没有无窗口的剪贴板回调：`AddClipboardFormatListener` 在内容变化时向目标窗口投递
 * `WM_CLIPBOARDUPDATE`。因此本类取主窗口句柄（`Native.getWindowID`）并对窗口过程做子类化，
 * 仅截获该消息、其余原样转发给原过程。
 */
internal object ClipboardChangeMonitor {
    private const val TAG = "DesktopClipboard"

    /** WM_CLIPBOARDUPDATE：剪贴板内容变化。 */
    private const val WM_CLIPBOARDUPDATE = 0x031D

    /** GWLP_WNDPROC：替换窗口过程。 */
    private const val GWLP_WNDPROC = -4

    @Volatile
    private var listener: (() -> Unit)? = null

    @Volatile
    private var targetWindow: Pointer? = null

    @Volatile
    private var originalProc: Pointer? = null

    /** 保持回调强引用，防止被 GC 回收导致原生侧悬空。 */
    private var wndProcRef: WndProcCallback? = null

    private val user32: User32? by lazy {
        if (!isWindows()) return@lazy null
        runCatching { Native.load("user32", User32::class.java) }
            .onFailure { Logger.w(TAG, "加载 user32 失败，剪贴板监听不可用", it) }
            .getOrNull()
    }

    /**
     * 在指定窗口上注册剪贴板变更监听；重复调用无副作用。
     *
     * @param hwnd 目标窗口句柄
     * @param onChanged 剪贴板变化回调（在窗口消息线程执行）
     * @return 是否注册成功
     */
    @Synchronized
    fun start(
        hwnd: Long,
        onChanged: () -> Unit,
    ): Boolean {
        if (hwnd == 0L) return false
        if (targetWindow != null) return true
        val library = user32 ?: return false
        val window = Pointer.createConstant(hwnd)

        listener = onChanged
        val callback =
            object : WndProcCallback {
                override fun invoke(
                    hWnd: Pointer?,
                    msg: Int,
                    wParam: Long,
                    lParam: Long,
                ): Long {
                    if (msg == WM_CLIPBOARDUPDATE) {
                        runCatching { listener?.invoke() }
                            .onFailure { Logger.w(TAG, "剪贴板回调失败", it) }
                    }
                    // 非本监听消息一律交回原窗口过程，不改变 AWT 既有行为
                    val previous = originalProc
                    return if (previous != null) {
                        library.CallWindowProcW(previous, hWnd, msg, wParam, lParam)
                    } else {
                        library.DefWindowProcW(hWnd, msg, wParam, lParam)
                    }
                }
            }
        wndProcRef = callback

        val previous = library.SetWindowLongPtrW(window, GWLP_WNDPROC, callback)
        if (previous == null) {
            Logger.w(TAG, "窗口过程子类化失败: " + Native.getLastError())
            wndProcRef = null
            listener = null
            return false
        }
        originalProc = previous
        targetWindow = window

        if (library.AddClipboardFormatListener(window) == 0) {
            // 注册失败则回滚子类化，避免留下悬挂的窗口过程
            runCatching { library.SetWindowLongPtrW(window, GWLP_WNDPROC, previous) }
            originalProc = null
            targetWindow = null
            wndProcRef = null
            listener = null
            Logger.w(TAG, "AddClipboardFormatListener 失败: " + Native.getLastError())
            return false
        }
        Logger.i(TAG, "剪贴板变更监听已注册（事件驱动）")
        return true
    }

    /** 注销监听并恢复原窗口过程；重复调用无副作用。 */
    @Synchronized
    fun stop() {
        val library = user32
        val window = targetWindow
        if (library != null && window != null) {
            runCatching { library.RemoveClipboardFormatListener(window) }
            originalProc?.let { runCatching { library.SetWindowLongPtrW(window, GWLP_WNDPROC, it) } }
        }
        targetWindow = null
        originalProc = null
        wndProcRef = null
        listener = null
    }

    /** 是否已成功注册。 */
    fun isActive(): Boolean = targetWindow != null

    private fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** user32 的剪贴板监听与窗口过程接口。 */
    internal interface User32 : Library {
        fun AddClipboardFormatListener(hwnd: Pointer): Int

        fun RemoveClipboardFormatListener(hwnd: Pointer): Int

        fun SetWindowLongPtrW(
            hwnd: Pointer,
            index: Int,
            newProc: WndProcCallback,
        ): Pointer?

        fun SetWindowLongPtrW(
            hwnd: Pointer,
            index: Int,
            newProc: Pointer,
        ): Pointer?

        fun CallWindowProcW(
            previous: Pointer,
            hwnd: Pointer?,
            msg: Int,
            wParam: Long,
            lParam: Long,
        ): Long

        fun DefWindowProcW(
            hwnd: Pointer?,
            msg: Int,
            wParam: Long,
            lParam: Long,
        ): Long
    }

    /** 窗口过程回调。 */
    internal interface WndProcCallback : Callback {
        fun invoke(
            hWnd: Pointer?,
            msg: Int,
            wParam: Long,
            lParam: Long,
        ): Long
    }
}
