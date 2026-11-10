package notifyrelay.desktop.overlay.scene

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import notifyrelay.base.log.Logger
import java.io.ByteArrayOutputStream

/**
 * 命令管道的按行读取器。
 *
 * 用 PeekNamedPipe 轮询到「确实有数据」才读取，因此读调用不会长时间阻塞；
 * 存量不足一行时把已读字节留在缓冲区，等下一轮凑齐，
 * 避免按行切分依赖一次 ReadFile 恰好返回整行。
 */
internal class LineReader(
    private val handle: WinNT.HANDLE,
) {
    private val buffer = ByteArrayOutputStream()

    /** 管道已断；由读失败置位，供上层在无报文时区分「空闲」与「断开」。 */
    var closed: Boolean = false
        private set

    /**
     * 读取一行报文。
     *
     * @param timeoutMs 本轮最长等待时间；超时且无完整行时返回 null，
     *   调用方据此推进心跳检查，而不是把它当作断开。
     */
    fun readMessage(timeoutMs: Long): SceneMessage? {
        if (closed) return null
        val deadline = System.currentTimeMillis() + timeoutMs
        val chunk = ByteArray(READ_CHUNK)

        while (true) {
            // 缓冲区里已有完整行就直接取，不必再等管道
            extractLine()?.let { return decode(it) }
            if (closed) return null

            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return null

            val available = IntByReference()
            if (!kernel32.PeekNamedPipe(handle, null, 0, null, available, null)) {
                val error = kernel32.GetLastError()
                if (error != ERROR_BROKEN_PIPE && error != ERROR_NO_DATA) {
                    Logger.w(TAG, "Peek 命名管道失败 错误码=" + error)
                }
                closed = true
                return null
            }

            if (available.value <= 0) {
                Thread.sleep(POLL_SLEEP_MS)
                continue
            }

            val want = minOf(available.value, chunk.size)
            val read = IntByReference()
            if (!kernel32.ReadFile(handle, chunk, want, read, null) || read.value <= 0) {
                closed = true
                return null
            }
            buffer.write(chunk, 0, read.value)
        }
    }

    /** 从缓冲区切出一整行（不含换行符）；没有完整行时返回 null。 */
    private fun extractLine(): String? {
        val bytes = buffer.toByteArray()
        val index = bytes.indexOf(NEWLINE)
        if (index < 0) return null
        buffer.reset()
        if (index + 1 < bytes.size) buffer.write(bytes, index + 1, bytes.size - index - 1)
        val line = bytes.copyOfRange(0, index)
        return String(line, Charsets.UTF_8)
    }

    private fun decode(line: String): SceneMessage? {
        if (line.isBlank()) return null
        return runCatching { SceneMessage.parse(line) }
            .onFailure { Logger.w(TAG, "报文解析失败 长度=" + line.length, it) }
            .getOrNull()
    }

    private fun ByteArray.indexOf(value: Byte): Int {
        for (i in indices) if (this[i] == value) return i
        return -1
    }

    private companion object {
        private const val TAG = "OverlayScenePipe"
        private const val NEWLINE = '\n'.code.toByte()
        private const val READ_CHUNK = 8 * 1024
        private const val POLL_SLEEP_MS = 15L
        private const val ERROR_BROKEN_PIPE = 109
        private const val ERROR_NO_DATA = 232

        private val kernel32: Kernel32 = Kernel32.INSTANCE
    }
}
