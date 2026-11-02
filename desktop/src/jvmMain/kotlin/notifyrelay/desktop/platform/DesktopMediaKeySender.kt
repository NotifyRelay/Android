package notifyrelay.desktop.platform

import com.sun.jna.Library
import com.sun.jna.Native
import notifyrelay.base.log.Logger

/**
 * 媒体键注入。
 *
 * 用 Win32 `keybd_event` 发送系统媒体键（VK_MEDIA_*），由当前持有媒体会话的应用响应；
 * 与 SMTC 的会话级 API 相比不依赖会话对象，也不需要该桥提供控制导出。
 *
 * 非 Windows 或注入失败时返回 false，调用方只记录日志。
 */
internal object DesktopMediaKeySender {
    private const val TAG = "DesktopMediaKey"

    /** keybd_event 的按下与抬起标志。 */
    private const val KEYEVENTF_KEYUP = 0x0002

    /** 媒体键虚拟码。 */
    private const val VK_MEDIA_NEXT_TRACK = 0xB0
    private const val VK_MEDIA_PREV_TRACK = 0xB1
    private const val VK_MEDIA_STOP = 0xB2
    private const val VK_MEDIA_PLAY_PAUSE = 0xB3

    private val user32: User32? by lazy {
        if (!isWindows()) return@lazy null
        runCatching { Native.load("user32", User32::class.java) }
            .onFailure { Logger.w(TAG, "加载 user32 失败，媒体键不可用", it) }
            .getOrNull()
    }

    /** 播放/暂停。 */
    fun playPause(): Boolean = tap(VK_MEDIA_PLAY_PAUSE)

    /** 上一首。 */
    fun previous(): Boolean = tap(VK_MEDIA_PREV_TRACK)

    /** 下一首。 */
    fun next(): Boolean = tap(VK_MEDIA_NEXT_TRACK)

    /** 停止。 */
    fun stop(): Boolean = tap(VK_MEDIA_STOP)

    /** 发送一次按键（按下 + 抬起）。 */
    private fun tap(keyCode: Int): Boolean {
        val library = user32 ?: return false
        return runCatching {
            library.keybd_event(keyCode.toByte(), 0.toByte(), 0, 0)
            library.keybd_event(keyCode.toByte(), 0.toByte(), KEYEVENTF_KEYUP, 0)
            true
        }.onFailure { Logger.w(TAG, "媒体键注入失败: " + keyCode, it) }
            .getOrDefault(false)
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** user32 的按键注入接口。 */
    internal interface User32 : Library {
        fun keybd_event(
            virtualKey: Byte,
            scanCode: Byte,
            flags: Int,
            extraInfo: Int,
        )
    }
}
