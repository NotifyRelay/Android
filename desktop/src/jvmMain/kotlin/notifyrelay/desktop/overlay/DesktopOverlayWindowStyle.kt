package notifyrelay.desktop.overlay

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.win32.StdCallLibrary
import notifyrelay.base.log.Logger
import java.awt.Window

/**
 * 叠加层窗口的 Win32 样式。
 *
 * 目标：全屏覆盖、点击穿透、不抢焦点、不进任务栏。
 * 与 C# 叠加层原实现同一组扩展样式。
 */
internal object DesktopOverlayWindowStyle {
    private const val TAG = "DesktopOverlayWin"

    private const val GWL_EXSTYLE = -20

    private const val WS_EX_LAYERED = 0x00080000
    private const val WS_EX_TRANSPARENT = 0x00000020
    private const val WS_EX_NOACTIVATE = 0x08000000
    private const val WS_EX_TOOLWINDOW = 0x00000080

    private interface User32 : StdCallLibrary {
        fun GetWindowLongPtrW(
            hWnd: Long,
            nIndex: Int,
        ): Long

        fun SetWindowLongPtrW(
            hWnd: Long,
            nIndex: Int,
            dwNewLong: Long,
        ): Long
    }

    private val user32: User32? by lazy {
        runCatching { Native.load("user32", User32::class.java) }.getOrNull()
    }

    /**
     * 应用点击穿透与不抢焦点样式。
     *
     * @return 是否成功应用
     */
    fun apply(window: Window): Boolean {
        if (!Platform.isWindows()) return false
        val lib = user32 ?: return false
        val hwnd =
            runCatching { Native.getWindowID(window) }.getOrElse {
                Logger.w(TAG, "取叠加层窗口句柄失败", it)
                return false
            }
        if (hwnd == 0L) return false
        return runCatching {
            val current = lib.GetWindowLongPtrW(hwnd, GWL_EXSTYLE)
            val target =
                current or
                    WS_EX_LAYERED.toLong() or
                    WS_EX_TRANSPARENT.toLong() or
                    WS_EX_NOACTIVATE.toLong() or
                    WS_EX_TOOLWINDOW.toLong()
            lib.SetWindowLongPtrW(hwnd, GWL_EXSTYLE, target)
            true
        }.getOrElse {
            Logger.w(TAG, "设置叠加层窗口样式失败", it)
            false
        }
    }
}
