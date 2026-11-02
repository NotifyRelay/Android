package notifyrelay.desktop.platform

import notifyrelay.base.log.Logger
import wtf.magma.audio.AudioCaptureListener
import wtf.magma.audio.SMTCLoader

/**
 * 桌面端系统声音采集器（WASAPI 回环，经 smtc-rust 的 cpal 桥）。
 *
 * 采集在 Rust 侧完成：对默认渲染设备打开输入流即自动置
 * `AUDCLNT_STREAMFLAGS_LOOPBACK`，无需声明独占或设置虚拟声卡。
 * PCM 帧经回调交给 [onPcm]，由调用方推流（对应中继模式的 send 方向）。
 */
internal object DesktopPcmCapture {
    private const val TAG = "DesktopCapture"

    /** 当前是否在采集。 */
    val isRunning: Boolean get() = SMTCLoader.available && runCatching { SMTCLoader.isAudioCapturing() == 1 }.getOrDefault(false)

    /** 采集流格式：`(采样率, 声道数)`；未运行时为 `(0, 0)`。 */
    fun format(): Pair<Int, Int> {
        if (!SMTCLoader.available) return 0 to 0
        val arr = runCatching { SMTCLoader.audioCaptureFormat() }.getOrNull() ?: return 0 to 0
        if (arr.size < 2) return 0 to 0
        return arr[0] to arr[1]
    }

    /**
     * 启动采集。
     *
     * @param onPcm 每帧 16 位小端交错 PCM 的回调
     * @return 是否成功启动
     */
    fun start(onPcm: (ByteArray) -> Unit): Boolean {
        if (!SMTCLoader.available) {
            Logger.w(TAG, "音频采集库不可用")
            return false
        }
        if (isRunning) return true
        val listener =
            object : AudioCaptureListener {
                override fun onAudioPcm(pcm: ByteArray) {
                    onPcm(pcm)
                }
            }
        val ret = runCatching { SMTCLoader.startAudioCapture(listener) }.getOrDefault(-1)
        if (ret != 0) {
            Logger.w(TAG, "启动系统声音采集失败: " + ret)
            return false
        }
        val (rate, channels) = format()
        Logger.i(TAG, "系统声音采集已启动: " + rate + "Hz/" + channels + "ch")
        return true
    }

    /** 停止采集。 */
    fun stop() {
        if (!SMTCLoader.available) return
        runCatching { SMTCLoader.stopAudioCapture() }
            .onFailure { Logger.w(TAG, "停止系统声音采集失败", it) }
    }
}
