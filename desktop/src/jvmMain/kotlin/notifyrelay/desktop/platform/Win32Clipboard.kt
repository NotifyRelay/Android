package notifyrelay.desktop.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.BaseTSD.SIZE_T
import notifyrelay.base.log.Logger

/**
 * Win32 剪贴板纯文本写入（JNA 调 user32 / kernel32）。
 *
 * AWT 在 Windows 上写 `CF_UNICODETEXT` 时会把每个 `\n` 换成 `\r\n`，
 * 且无法在业务层关闭（换 DataFlavor 或 `line.separator` 均无效）。
 * 本类直接写原生格式，使本机剪贴板与远端内容同口径。
 *
 * 与 Windows 端 `ClipboardService` 同口径：换行统一为 `\n`。
 */
internal object Win32Clipboard {
    private const val TAG = "DesktopClipboard"

    /** CF_UNICODETEXT：Unicode 纯文本格式号。 */
    private const val CF_UNICODETEXT = 13

    /** GMEM_MOVEABLE：可移动全局内存，`SetClipboardData` 要求该标志。 */
    private const val GMEM_MOVEABLE = 0x0002

    /** 打开剪贴板的重试次数：剪贴板被其他进程短暂占用时重试。 */
    private const val OPEN_RETRY_COUNT = 10

    /** 打开剪贴板的重试间隔（毫秒）。 */
    private const val OPEN_RETRY_DELAY_MS = 20L

    private val user32: User32? by lazy {
        if (!isWindows()) return@lazy null
        runCatching { Native.load("user32", User32::class.java) }
            .onFailure { Logger.w(TAG, "加载 user32 失败，Win32 剪贴板不可用", it) }
            .getOrNull()
    }

    private val kernel32: Kernel32? by lazy {
        if (!isWindows()) return@lazy null
        runCatching { Native.load("kernel32", Kernel32::class.java) }
            .onFailure { Logger.w(TAG, "加载 kernel32 失败，Win32 剪贴板不可用", it) }
            .getOrNull()
    }

    /**
     * 换行归一：`CRLF` → `LF`。
     *
     * 只改写 `\r\n` 这一个二元组，其余字符（含孤立 `\r`、`\t`、代理对）原样保留；
     * 对已归一内容再执行无变化，收发两侧重复调用安全。
     */
    fun normalizeNewlines(text: String): String = text.replace("\r\n", "\n")

    /** Win32 剪贴板通道是否可用；非 Windows 或原生库加载失败时为 false。 */
    fun isAvailable(): Boolean = user32 != null && kernel32 != null

    /**
     * 以 `CF_UNICODETEXT` 写入纯文本，写前归一换行。
     *
     * @param text 待写入文本
     * @return 写入成功返回 true；打开剪贴板失败、内存分配失败或系统拒收返回 false
     */
    fun writeText(text: String): Boolean {
        val u = user32 ?: return false
        val k = kernel32 ?: return false
        val normalized = normalizeNewlines(text)
        // UTF-16LE：每码元 2 字节，末尾补一个 NUL
        val byteCount = (normalized.length + 1) * 2L

        if (!openClipboard(u)) return false
        try {
            if (u.EmptyClipboard() == 0) return false
            val handle = k.GlobalAlloc(GMEM_MOVEABLE, SIZE_T(byteCount)) ?: return false
            val locked = k.GlobalLock(handle)
            if (locked == null) {
                k.GlobalFree(handle)
                return false
            }
            try {
                locked.setWideString(0, normalized)
            } finally {
                k.GlobalUnlock(handle)
            }
            if (u.SetClipboardData(CF_UNICODETEXT, handle) == null) {
                k.GlobalFree(handle)
                return false
            }
            // SetClipboardData 成功后所有权移交系统，此后不得 GlobalFree
            return true
        } finally {
            u.CloseClipboard()
        }
    }

    /** 循环重试打开剪贴板；被其他进程占用时短暂等待。 */
    private fun openClipboard(u: User32): Boolean {
        repeat(OPEN_RETRY_COUNT) {
            if (u.OpenClipboard(null) != 0) return true
            runCatching { Thread.sleep(OPEN_RETRY_DELAY_MS) }
        }
        Logger.w(TAG, "打开剪贴板失败：可能被其他进程占用")
        return false
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** user32 的剪贴板接口。 */
    internal interface User32 : Library {
        fun OpenClipboard(hWndNewOwner: Pointer?): Int

        fun CloseClipboard(): Int

        fun EmptyClipboard(): Int

        fun SetClipboardData(
            format: Int,
            mem: Pointer?,
        ): Pointer?
    }

    /** kernel32 的全局内存接口。 */
    internal interface Kernel32 : Library {
        fun GlobalAlloc(
            flags: Int,
            bytes: SIZE_T,
        ): Pointer?

        fun GlobalLock(handle: Pointer): Pointer?

        fun GlobalUnlock(handle: Pointer): Int

        fun GlobalFree(handle: Pointer): Pointer?
    }
}
