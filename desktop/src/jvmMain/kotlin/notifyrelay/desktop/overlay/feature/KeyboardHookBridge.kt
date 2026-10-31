package notifyrelay.desktop.overlay.feature

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef.HMODULE
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser.HHOOK
import com.sun.jna.platform.win32.WinUser.KBDLLHOOKSTRUCT
import com.sun.jna.platform.win32.WinUser.LowLevelKeyboardProc
import com.sun.jna.platform.win32.WinUser.MSG
import com.sun.jna.win32.StdCallLibrary

/** Win32 键盘钩子相关常量。 */
internal object Win32Keyboard {
    /** 低级键盘钩子类型。 */
    const val WH_KEYBOARD_LL = 13

    /** 键盘抬起消息。 */
    const val WM_KEYUP = 0x0101

    /** 系统键抬起消息。 */
    const val WM_SYSKEYUP = 0x0105

    /** 线程消息队列退出消息。 */
    const val WM_QUIT = 0x0012

    /** 切换键（锁定键）虚拟键码：Caps Lock / Num Lock / Scroll Lock。 */
    val TOGGLE_KEYS = intArrayOf(0x14, 0x90, 0x91)

    /** 修饰键虚拟键码：Shift / Ctrl / Alt；钩子漏掉边沿时按实时状态补偿。 */
    val MODIFIER_KEYS = intArrayOf(0x10, 0x11, 0x12)
}

/**
 * 钩子与消息循环所需的 user32 入口。
 *
 * 独立声明而非复用 `jna-platform` 的 User32 门面：此处需要
 * `SetWindowsHookExW` / `GetMessageW` / `PostThreadMessageW` 等未暴露的入口。
 */
internal interface User32Ex : StdCallLibrary {
    fun SetWindowsHookExW(
        idHook: Int,
        lpfn: LowLevelKeyboardProc,
        hMod: HMODULE?,
        dwThreadId: Int,
    ): HHOOK?

    fun UnhookWindowsHookEx(hhk: HHOOK): Boolean

    fun CallNextHookEx(
        hhk: HHOOK?,
        nCode: Int,
        wParam: WPARAM,
        lParam: LPARAM,
    ): LRESULT

    fun GetKeyState(nVirtKey: Int): Short

    fun GetMessageW(
        lpMsg: MSG,
        hWnd: HWND?,
        wMsgFilterMin: Int,
        wMsgFilterMax: Int,
    ): Int

    fun TranslateMessage(lpMsg: MSG): Boolean

    fun DispatchMessageW(lpMsg: MSG): LRESULT

    fun PostThreadMessageW(
        idThread: Int,
        msg: Int,
        wParam: WPARAM,
        lParam: LPARAM,
    ): Boolean

    companion object {
        /** JNA 按接口代理加载 user32。 */
        val INSTANCE: User32Ex by lazy {
            com.sun.jna.Native
                .load("user32", User32Ex::class.java)
        }
    }
}

/**
 * 低级键盘钩子的原生封装。
 *
 * `WH_KEYBOARD_LL` 的回调由系统投递到**安装钩子的线程**，该线程必须持续泵消息
 * 才会收到回调；安装、消息循环与卸载均由 [DesktopKeyboardSource] 的专用线程负责。
 *
 * 本类只封装「安装 / 卸载 / 投递退出消息 / 读取按键状态」四类入口。
 */
internal class KeyboardHookBridge(
    private val onKeyEvent: (vkCode: Int, isDown: Boolean) -> Unit,
) {
    private var hook: HHOOK? = null

    /** 安装钩子；已安装时返回 true（幂等）。 */
    fun install(): Boolean {
        if (hook != null) return true

        val proc =
            LowLevelKeyboardProc { nCode, wParam, info ->
                handleCallback(nCode, wParam, info)
            }
        // 低级键盘钩子的钩子过程在本进程内执行，模块句柄传主模块即可
        val module = Kernel32.INSTANCE.GetModuleHandle(null)
        val installed = User32Ex.INSTANCE.SetWindowsHookExW(Win32Keyboard.WH_KEYBOARD_LL, proc, module, 0)
        if (installed == null) {
            return false
        }
        hook = installed
        return true
    }

    /** 卸载钩子；未安装时返回 true（幂等）。 */
    fun uninstall(): Boolean {
        val current = hook ?: return true
        val ok = User32Ex.INSTANCE.UnhookWindowsHookEx(current)
        hook = null
        return ok
    }

    /** 钩子是否已安装。 */
    fun isInstalled(): Boolean = hook != null

    /**
     * 泵一次消息循环。
     *
     * 阻塞直到有消息到达；[Win32Keyboard.WM_QUIT] 到达时返回 false 结束循环。
     * 返回值仅表示是否继续，不表示消息处理结果。
     */
    fun pumpOnce(): Boolean {
        val msg = MSG()
        val result = User32Ex.INSTANCE.GetMessageW(msg, null, 0, 0)
        if (result <= 0) return false
        User32Ex.INSTANCE.TranslateMessage(msg)
        User32Ex.INSTANCE.DispatchMessageW(msg)
        return true
    }

    /** 投递退出消息，唤醒阻塞中的消息循环。 */
    fun postQuit(threadId: Int) {
        User32Ex.INSTANCE.PostThreadMessageW(threadId, Win32Keyboard.WM_QUIT, WPARAM(0), LPARAM(0))
    }

    /** 当前线程 id，供 [postQuit] 使用；该函数属于 kernel32 而非 user32。 */
    fun currentThreadId(): Int = Kernel32.INSTANCE.GetCurrentThreadId()

    /** 切换键当前锁定状态。 */
    fun isToggled(vkCode: Int): Boolean = (User32Ex.INSTANCE.GetKeyState(vkCode).toInt() and 0x0001) != 0

    /** 按键当前是否物理按下。 */
    fun isDown(vkCode: Int): Boolean = (User32Ex.INSTANCE.GetKeyState(vkCode).toInt() and 0x8000) != 0

    /** 回调主体：只做状态判定与转发，不做任何可能阻塞的工作。 */
    private fun handleCallback(
        nCode: Int,
        wParam: WPARAM,
        info: KBDLLHOOKSTRUCT,
    ): LRESULT {
        if (nCode >= 0) {
            runCatching {
                val message = wParam.toInt()
                val isDown = message != Win32Keyboard.WM_KEYUP && message != Win32Keyboard.WM_SYSKEYUP
                onKeyEvent(info.vkCode, isDown)
            }
        }
        return User32Ex.INSTANCE.CallNextHookEx(null, nCode, wParam, LPARAM(0))
    }
}
