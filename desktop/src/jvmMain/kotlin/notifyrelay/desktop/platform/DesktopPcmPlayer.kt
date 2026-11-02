package notifyrelay.desktop.platform

import notifyrelay.base.log.Logger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/**
 * 桌面端音频中继的 PCM 播放器（JavaSound）。
 *
 * Rust 的接收端只负责 UDP 收流、Opus 解码并通过数据回调交出 PCM，
 * 播放由平台完成；本类以 16 位小端 PCM 流式写入系统默认输出设备。
 * 对应 Android 端 `AudioRelayPlayer` 的 `AudioTrack` 分支。
 */
internal object DesktopPcmPlayer {
    private const val TAG = "DesktopPcm"

    @Volatile
    private var line: SourceDataLine? = null

    @Volatile
    private var running: Boolean = false

    /** 播放是否进行中。 */
    val isRunning: Boolean get() = running

    /**
     * 打开播放通道。
     *
     * @param sampleRate 采样率
     * @param channels 声道数
     * @return 是否成功打开
     */
    fun open(
        sampleRate: Int = 48000,
        channels: Int = 2,
    ): Boolean {
        if (running) return true
        return try {
            val format = AudioFormat(sampleRate.toFloat(), 16, channels, true, false)
            val info = DataLine.Info(SourceDataLine::class.java, format)
            val opened = AudioSystem.getLine(info) as SourceDataLine
            opened.open(format, BUFFER_BYTES)
            opened.start()
            line = opened
            running = true
            Logger.i(TAG, "音频播放通道已打开: " + sampleRate + "Hz/" + channels + "ch")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "打开音频播放通道失败", e)
            running = false
            false
        }
    }

    /**
     * 写入一帧 PCM（16 位小端）。
     *
     * @return 是否写入成功
     */
    fun write(
        pcm: ByteArray,
        length: Int,
    ): Boolean {
        val current = line ?: return false
        return try {
            current.write(pcm, 0, length)
            true
        } catch (e: Exception) {
            Logger.w(TAG, "写入音频帧失败", e)
            false
        }
    }

    /** 关闭播放通道。 */
    fun close() {
        running = false
        val current = line
        line = null
        if (current != null) {
            runCatching {
                current.stop()
                current.flush()
                current.close()
            }.onFailure { Logger.w(TAG, "关闭音频播放通道失败", it) }
        }
        Logger.i(TAG, "音频播放通道已关闭")
    }

    /** 播放缓冲区字节数。 */
    private const val BUFFER_BYTES = 65536
}
