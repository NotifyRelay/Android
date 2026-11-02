package notifyrelay.data.config

/** scrcpy 选项项：下标、命令行片段与显示名。 */
data class ScrcpyOption(
    val id: Int,
    val command: String,
    val display: String,
)

/**
 * scrcpy 的固定选项表与命令行生成。
 *
 * 内容对齐 Windows 端 `ScrcpyPreferences` 与 `ScrcpyConfigBuilder`：
 * 下标顺序与 Command 字符串逐字一致，直接按设置里的下标取用。
 */
object ScrcpyOptions {
    /** 显示方向。 */
    val displayOrientations: List<ScrcpyOption> =
        listOf(
            ScrcpyOption(0, "", "默认"),
            ScrcpyOption(1, "0", "0°"),
            ScrcpyOption(2, "90", "90°"),
            ScrcpyOption(3, "180", "180°"),
            ScrcpyOption(4, "270", "270°"),
            ScrcpyOption(5, "flip0", "flip-0°"),
            ScrcpyOption(6, "flip90", "flip-90°"),
            ScrcpyOption(7, "flip180", "flip-180°"),
            ScrcpyOption(8, "flip270", "flip-270°"),
        )

    /** 视频编解码；id 与下标不一致是既有语义，按列表下标取用。 */
    val videoCodecs: List<ScrcpyOption> =
        listOf(
            ScrcpyOption(0, "", "默认"),
            ScrcpyOption(1, "--video-codec=h264 --video-encoder=OMX.qcom.video.encoder.avc", "h264 & c2.qti.avc.encoder (hw)"),
            ScrcpyOption(2, "--video-codec=h264 --video-encoder=c2.android.avc.encoder", "h264 & c2.android.avc.encoder (sw)"),
            ScrcpyOption(4, "--video-codec=h264 --video-encoder=OMX.google.h264.encoder", "h264 & OMX.google.h264.encoder (sw)"),
            ScrcpyOption(5, "--video-codec=h265 --video-encoder=OMX.qcom.video.encoder.hevc", "h265 & OMX.qcom.video.encoder.hevc (hw)"),
            ScrcpyOption(6, "--video-codec=h265 --video-encoder=c2.android.hevc.encoder", "h265 & c2.android.hevc.encoder (sw)"),
        )

    /** 音频编解码。 */
    val audioCodecs: List<ScrcpyOption> =
        listOf(
            ScrcpyOption(0, "", "默认"),
            ScrcpyOption(1, "--audio-codec=opus --audio-encoder=c2.android.opus.encoder", "opus & c2.android.opus.encoder (sw)"),
            ScrcpyOption(2, "--audio-codec=aac --audio-encoder=c2.android.aac.encoder", "aac & c2.android.aac.encoder (sw)"),
            ScrcpyOption(3, "--audio-codec=aac --audio-encoder=OMX.google.aac.encoder", "aac & OMX.google.aac.encoder (sw)"),
            ScrcpyOption(4, "--audio-codec=raw", "raw"),
        )

    /**
     * 生成 scrcpy 参数列表。
     *
     * 追加顺序对齐 Windows `ScrcpyConfigBuilder.Build`（仅音频模式单独短路）；
     * 返回值为参数列表，不含可执行文件本身。`startApp` 非空时作为首个参数前置
     * （对应 Windows 端「应用列表项点击启动」的 `--start-app=...`）。
     *
     * @param settings 设备配置
     * @param serial 目标设备序列号（`adb devices` 中的标识）
     * @param startApp 启动后要拉起的包名；为空表示仅投屏
     * @param windowTitle 窗口标题；仅在 [startApp] 非空时生效（对齐 Windows 的应用启动带标题）
     */
    fun buildArguments(
        settings: ScrcpySettings,
        serial: String,
        startApp: String = "",
        windowTitle: String = "",
    ): List<String> {
        val args = mutableListOf<String>()
        args.add("-s")
        args.add(serial)

        // 仅音频模式：最小命令集，不叠加视频参数
        val audioOnly = settings.disableVideoForwarding
        if (audioOnly) {
            args.add("--no-video")
            args.add("--audio-source=playback")
            args.add("--audio-dup")
            formatBitrate(settings.audioBitrate, "K")?.let { args.add("--audio-bit-rate=" + it) }
            if (settings.audioBuffer.isNotBlank()) args.add("--audio-buffer=" + settings.audioBuffer)
            if (settings.audioOutputBuffer.isNotBlank()) args.add("--audio-output-buffer=" + settings.audioOutputBuffer)
            return args
        }

        // 启动应用参数前置（与 Windows 端 args[0].StartsWith("--start-app") 的判定顺序对应）
        if (startApp.isNotBlank()) {
            args.add("--start-app=" + startApp)
            if (windowTitle.isNotBlank()) args.add("--window-title=" + windowTitle)
        }

        if (settings.customArguments.isNotBlank()) {
            args.addAll(
                settings.customArguments
                    .trim()
                    .split(WHITESPACE)
                    .filter { it.isNotBlank() },
            )
        }

        // 通用
        if (settings.screenOff) args.add("--turn-screen-off")
        if (settings.physicalKeyboard) args.add("--keyboard=uhid")

        // 视频
        videoCodecs.getOrNull(settings.videoCodec)?.command?.takeIf { it.isNotBlank() }?.let {
            args.addAll(it.split(WHITESPACE).filter { part -> part.isNotBlank() })
        }
        if (settings.videoResolution.isNotBlank()) args.add("--max-size=" + settings.videoResolution)
        formatBitrate(settings.videoBitrate, "M")?.let { args.add("--video-bit-rate=" + it) }
        if (settings.videoBuffer.isNotBlank()) args.add("--video-buffer=" + settings.videoBuffer)
        if (settings.frameRate.isNotBlank()) args.add("--max-fps=" + settings.frameRate)
        if (settings.crop.isNotBlank()) args.add("--crop=" + settings.crop)
        displayOrientations.getOrNull(settings.displayOrientation)?.command?.takeIf { it.isNotBlank() }?.let {
            args.add("--orientation=" + it)
        }
        if (settings.display.isNotBlank()) args.add("--display-id=" + settings.display)

        // 音频
        formatBitrate(settings.audioBitrate, "K")?.let { args.add("--audio-bit-rate=" + it) }
        if (settings.audioBuffer.isNotBlank()) args.add("--audio-buffer=" + settings.audioBuffer)
        if (settings.audioOutputBuffer.isNotBlank()) args.add("--audio-output-buffer=" + settings.audioOutputBuffer)
        if (settings.forwardMicrophone) args.add("--audio-source=mic")
        when (settings.audioOutputMode) {
            ScrcpyAudioOutputMode.REMOTE -> args.add("--no-audio")
            ScrcpyAudioOutputMode.BOTH -> args.add("--audio-dup")
            ScrcpyAudioOutputMode.DESKTOP -> Unit
        }
        audioCodecs.getOrNull(settings.audioCodec)?.command?.takeIf { it.isNotBlank() }?.let {
            args.addAll(it.split(WHITESPACE).filter { part -> part.isNotBlank() })
        }

        // 虚拟显示：仅在带 --start-app 启动应用时生效（对齐 Windows ScrcpyConfigBuilder），
        // 且受 virtualDisplayEnabled 开关控制；未启用虚拟显示时由调用方终止既存进程。
        if (startApp.isNotBlank() && settings.virtualDisplayEnabled) {
            if (settings.virtualDisplaySize.isNotBlank()) {
                args.add("--new-display=" + settings.virtualDisplaySize)
            } else {
                args.add("--new-display")
            }
        }

        return args
    }

    /**
     * 码率格式化。
     *
     * 配置存数值（对齐 Android 端 `video_bit_rate_input` / `audio_bit_rate_input`），
     * 而 scrcpy 命令行要求带单位：纯数字按 [unit] 补后缀，已带 M/K/G 则原样返回。
     *
     * @return 可用的码率文本；无法解析时返回 null（跳过该参数）
     */
    private fun formatBitrate(
        raw: String,
        unit: String,
    ): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val last = text.last().uppercaseChar()
        if (last == 'M' || last == 'K' || last == 'G') return text
        val number = text.toDoubleOrNull() ?: return null
        val normalized = if (number == number.toLong().toDouble()) number.toLong().toString() else number.toString()
        return normalized + unit
    }

    private val WHITESPACE = Regex("\\s+")
}
