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
     * @param iconPath 图标路径（注入 `SCRCPY_ICON_PATH` 由调用方负责）
     */
    fun buildArguments(
        settings: ScrcpySettings,
        serial: String,
        startApp: String = "",
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
            if (settings.audioBitrate.isNotBlank()) args.add("--audio-bit-rate=" + settings.audioBitrate)
            if (settings.audioBuffer.isNotBlank()) args.add("--audio-buffer=" + settings.audioBuffer)
            if (settings.audioOutputBuffer.isNotBlank()) args.add("--audio-output-buffer=" + settings.audioOutputBuffer)
            return args
        }

        // 启动应用参数前置（与 Windows 端 args[0].StartsWith("--start-app") 的判定顺序对应）
        if (startApp.isNotBlank()) {
            args.add("--start-app=" + startApp)
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
        if (settings.videoBitrate.isNotBlank()) args.add("--video-bit-rate=" + settings.videoBitrate)
        if (settings.videoBuffer.isNotBlank()) args.add("--video-buffer=" + settings.videoBuffer)
        if (settings.frameRate.isNotBlank()) args.add("--max-fps=" + settings.frameRate)
        if (settings.crop.isNotBlank()) args.add("--crop=" + settings.crop)
        displayOrientations.getOrNull(settings.displayOrientation)?.command?.takeIf { it.isNotBlank() }?.let {
            args.add("--orientation=" + it)
        }
        if (settings.display.isNotBlank()) args.add("--display-id=" + settings.display)

        // 音频
        if (settings.audioBitrate.isNotBlank()) args.add("--audio-bit-rate=" + settings.audioBitrate)
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

        // 虚拟显示（与 Windows 端一致：仅启用虚拟显示时追加；未启用则终止既存进程，由调用方处理）
        if (settings.virtualDisplaySize.isNotBlank()) {
            args.add("--new-display=" + settings.virtualDisplaySize)
        }

        return args
    }

    private val WHITESPACE = Regex("\\s+")
}
