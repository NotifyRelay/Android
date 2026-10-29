package notifyrelay.desktop.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary

/**
 * 会话锁屏状态查询。
 *
 * 判定依据：能否打开输入桌面 `Default`。锁屏后输入桌面被系统切换到
 * `Winlogon`，故对 `Default` 的 OpenInputDesktop 会失败。
 * 与 Windows `LocalNotificationListenerService.IsWorkstationLocked` 同思路。
 */
internal object DesktopSessionLock {
    /** 桌面访问权限：DESKTOP_SWITCHDESKTOP，足以探测输入桌面归属。 */
    private const val DESKTOP_SWITCHDESKTOP = 0x0100

    private interface User32Ex : StdCallLibrary {
        fun OpenInputDesktop(
            dwFlags: Int,
            fInherit: Boolean,
            dwDesiredAccess: Int,
        ): Pointer?

        fun CloseDesktop(hDesktop: Pointer?): Boolean
    }

    private val user32: User32Ex? by lazy {
        runCatching { Native.load("user32", User32Ex::class.java) }.getOrNull()
    }

    /**
     * 会话是否已锁屏。
     *
     * @return true 已锁屏；API 不可用或调用异常时返回 false（不把未知当锁屏）
     */
    fun isLocked(): Boolean {
        val lib = user32 ?: return false
        return runCatching {
            val desktop = lib.OpenInputDesktop(0, false, DESKTOP_SWITCHDESKTOP)
            if (desktop == null) {
                true
            } else {
                lib.CloseDesktop(desktop)
                false
            }
        }.getOrDefault(false)
    }
}
