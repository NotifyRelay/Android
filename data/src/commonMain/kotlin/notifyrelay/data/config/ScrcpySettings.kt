package notifyrelay.data.config

import notifyrelay.data.Storage

/**
 * scrcpy 启动配置。
 *
 * 分两层持久化，与 Android 端的既有方案一致：
 * - **投屏参数为全局一套**：键名直接沿用 [ScrcpyPreferenceKeys]（`max_size_input`、
 *   `video_bit_rate_input`、`turn_screen_off` 等），与 Android 端读写同一组键；
 * - **解锁配置按设备**：`unlockBeforeLaunch` / `unlockTimeout` / `unlockCommands`，
 *   键前缀 `scrcpy_unlock_<deviceUuid>_`。
 */
data class ScrcpySettings(
    /** 关闭设备屏幕（scrcpy 侧仍投屏）。 */
    val screenOff: Boolean = false,
    /** 物理键盘以 UHID 方式接入。 */
    val physicalKeyboard: Boolean = false,
    /** 视频码率，如 `8M`。 */
    val videoBitrate: String = "8.0",
    /** 最大边像素，空表示不限制。 */
    val videoResolution: String = "",
    /** 视频缓冲（毫秒）。 */
    val videoBuffer: String = "",
    /** 音频码率，如 `128K`。 */
    val audioBitrate: String = "128",
    /** 音频缓冲（毫秒）。 */
    val audioBuffer: String = "",
    /** 音频输出缓冲（毫秒）。 */
    val audioOutputBuffer: String = "",
    /** 视频编解码选项下标（见 [ScrcpyOptions.videoCodecs]）。 */
    val videoCodec: Int = 0,
    /** 音频编解码选项下标（见 [ScrcpyOptions.audioCodecs]）。 */
    val audioCodec: Int = 0,
    /** 显示方向选项下标（见 [ScrcpyOptions.displayOrientations]）。 */
    val displayOrientation: Int = 0,
    /** 最大帧率。 */
    val frameRate: String = "",
    /** 裁剪区域 `width:height:x:y`。 */
    val crop: String = "",
    /** 显示 id。 */
    val display: String = "",
    /** 虚拟显示尺寸，如 `1920x1080`。 */
    val virtualDisplaySize: String = "",
    /** 是否启用虚拟显示（仅在带 `--start-app` 启动应用时生效）。 */
    val virtualDisplayEnabled: Boolean = true,
    /** 自定义附加参数（原样追加到命令行）。 */
    val customArguments: String = "",
    /** 仅音频转发（不投屏）。 */
    val disableVideoForwarding: Boolean = false,
    /** 音频输出模式。 */
    val audioOutputMode: ScrcpyAudioOutputMode = ScrcpyAudioOutputMode.DESKTOP,
    /** 转发麦克风。 */
    val forwardMicrophone: Boolean = false,
    /** 目标设备选择偏好（按设备配置）。 */
    val devicePreference: ScrcpyDevicePreference = ScrcpyDevicePreference.AUTO,
    /** 允许对 USB 设备启用 tcpip 以建立无线 adb（按设备配置）。 */
    val adbTcpipModeEnabled: Boolean = false,
    /** 启动前若设备锁屏则自动解锁（按设备配置）。 */
    val unlockBeforeLaunch: Boolean = false,
    /** 解锁密码缓存分钟数；0 表示每次都要输入。 */
    val unlockTimeout: Int = 0,
    /** 解锁命令序列，每行一条 adb shell 命令；`%pwd%` 会替换为输入的密码。 */
    val unlockCommands: String = "",
)

/** 目标设备选择偏好，对齐 Windows `ScrcpyDevicePreferenceType`。 */
enum class ScrcpyDevicePreference {
    /** 自动：优先 USB，其次无线。 */
    AUTO,

    /** 优先 USB。 */
    USB,

    /** 优先无线。 */
    TCPIP,

    /** 每次询问；桌面端按自动处理（无设备选择弹窗）。 */
    ASK_EVERY_TIME,
}

/** 音频输出模式，对齐 Windows `AudioOutputModeType`。 */
enum class ScrcpyAudioOutputMode {
    /** 仅桌面播放。 */
    DESKTOP,

    /** 仅远端播放（`--no-audio`）。 */
    REMOTE,

    /** 两端同时播放（`--audio-dup`）。 */
    BOTH,
}

/**
 * scrcpy 配置读写。
 *
 * 投屏参数存全局键（沿用 Android 端 [ScrcpyPreferenceKeys] 的键名，两平台共享同一份配置）；
 * 解锁配置按设备存，键前缀 `scrcpy_unlock_<deviceUuid>_`。
 */
object ScrcpySettingsStore {
    private const val UNLOCK_KEY_PREFIX = "scrcpy_unlock_"

    private fun globalKey(field: String) = field

    private fun unlockKey(
        deviceUuid: String,
        field: String,
    ): String = UNLOCK_KEY_PREFIX + deviceUuid + "_" + field

    /**
     * 读取配置。
     *
     * @param deviceUuid 当前设备；为空时只返回全局参数（解锁项取默认值）
     */
    fun load(deviceUuid: String?): ScrcpySettings {
        val d = ScrcpySettings()
        val global =
            ScrcpySettings(
                screenOff = Storage.getBoolean(globalKey(ScrcpyPreferenceKeys.TURN_SCREEN_OFF), d.screenOff, Storage.PrefsType.SCRCPY),
                physicalKeyboard = Storage.getBoolean(globalKey("physical_keyboard"), d.physicalKeyboard, Storage.PrefsType.SCRCPY),
                videoBitrate = Storage.getString(globalKey(ScrcpyPreferenceKeys.VIDEO_BIT_RATE_INPUT), d.videoBitrate, Storage.PrefsType.SCRCPY),
                videoResolution = Storage.getString(globalKey(ScrcpyPreferenceKeys.MAX_SIZE_INPUT), d.videoResolution, Storage.PrefsType.SCRCPY),
                videoBuffer = Storage.getString(globalKey("video_buffer"), d.videoBuffer, Storage.PrefsType.SCRCPY),
                audioBitrate = Storage.getString(globalKey(ScrcpyPreferenceKeys.AUDIO_BIT_RATE_INPUT), d.audioBitrate, Storage.PrefsType.SCRCPY),
                audioBuffer = Storage.getString(globalKey("audio_buffer"), d.audioBuffer, Storage.PrefsType.SCRCPY),
                audioOutputBuffer = Storage.getString(globalKey("audio_output_buffer"), d.audioOutputBuffer, Storage.PrefsType.SCRCPY),
                videoCodec = Storage.getInt(globalKey(ScrcpyPreferenceKeys.VIDEO_CODEC), d.videoCodec, Storage.PrefsType.SCRCPY),
                audioCodec = Storage.getInt(globalKey(ScrcpyPreferenceKeys.AUDIO_CODEC), d.audioCodec, Storage.PrefsType.SCRCPY),
                displayOrientation = Storage.getInt(globalKey("display_orientation"), d.displayOrientation, Storage.PrefsType.SCRCPY),
                frameRate = Storage.getString(globalKey(ScrcpyPreferenceKeys.MAX_FPS_INPUT), d.frameRate, Storage.PrefsType.SCRCPY),
                crop = Storage.getString(globalKey("crop"), d.crop, Storage.PrefsType.SCRCPY),
                display = Storage.getString(globalKey(ScrcpyPreferenceKeys.DISPLAY_ID), d.display, Storage.PrefsType.SCRCPY),
                virtualDisplaySize = Storage.getString(globalKey("virtual_display_size"), d.virtualDisplaySize, Storage.PrefsType.SCRCPY),
                virtualDisplayEnabled = Storage.getBoolean(globalKey("virtual_display_enabled"), d.virtualDisplayEnabled, Storage.PrefsType.SCRCPY),
                customArguments = Storage.getString(globalKey("custom_arguments"), d.customArguments, Storage.PrefsType.SCRCPY),
                disableVideoForwarding = Storage.getBoolean(globalKey(ScrcpyPreferenceKeys.NO_VIDEO), d.disableVideoForwarding, Storage.PrefsType.SCRCPY),
                audioOutputMode =
                    runCatching {
                        ScrcpyAudioOutputMode.valueOf(
                            Storage.getString(globalKey("audio_output_mode"), d.audioOutputMode.name, Storage.PrefsType.SCRCPY),
                        )
                    }.getOrDefault(d.audioOutputMode),
                forwardMicrophone = Storage.getBoolean(globalKey("forward_microphone"), d.forwardMicrophone, Storage.PrefsType.SCRCPY),
            )
        if (deviceUuid.isNullOrBlank()) return global
        return global.copy(
            unlockBeforeLaunch = Storage.getBoolean(unlockKey(deviceUuid, "before_launch"), d.unlockBeforeLaunch, Storage.PrefsType.SCRCPY),
            unlockTimeout = Storage.getInt(unlockKey(deviceUuid, "timeout"), d.unlockTimeout, Storage.PrefsType.SCRCPY),
            unlockCommands = Storage.getString(unlockKey(deviceUuid, "commands"), d.unlockCommands, Storage.PrefsType.SCRCPY),
            devicePreference =
                runCatching {
                    ScrcpyDevicePreference.valueOf(
                        Storage.getString(unlockKey(deviceUuid, "device_preference"), d.devicePreference.name, Storage.PrefsType.SCRCPY),
                    )
                }.getOrDefault(d.devicePreference),
            adbTcpipModeEnabled =
                Storage.getBoolean(unlockKey(deviceUuid, "adb_tcpip_mode"), d.adbTcpipModeEnabled, Storage.PrefsType.SCRCPY),
        )
    }

    /**
     * 保存配置：投屏参数写全局键，解锁配置写设备键。
     *
     * @param deviceUuid 当前设备；为空时只写全局参数
     */
    fun save(
        deviceUuid: String?,
        settings: ScrcpySettings,
    ) {
        Storage.putBoolean(globalKey(ScrcpyPreferenceKeys.TURN_SCREEN_OFF), settings.screenOff, Storage.PrefsType.SCRCPY)
        Storage.putBoolean(globalKey("physical_keyboard"), settings.physicalKeyboard, Storage.PrefsType.SCRCPY)
        Storage.putString(globalKey(ScrcpyPreferenceKeys.VIDEO_BIT_RATE_INPUT), settings.videoBitrate, Storage.PrefsType.SCRCPY)
        Storage.putString(globalKey(ScrcpyPreferenceKeys.MAX_SIZE_INPUT), settings.videoResolution, Storage.PrefsType.SCRCPY)
        Storage.putString(globalKey("video_buffer"), settings.videoBuffer, Storage.PrefsType.SCRCPY)
        Storage.putString(globalKey(ScrcpyPreferenceKeys.AUDIO_BIT_RATE_INPUT), settings.audioBitrate, Storage.PrefsType.SCRCPY)
        Storage.putString(globalKey("audio_buffer"), settings.audioBuffer, Storage.PrefsType.SCRCPY)
        Storage.putString(globalKey("audio_output_buffer"), settings.audioOutputBuffer, Storage.PrefsType.SCRCPY)
        Storage.putInt(globalKey(ScrcpyPreferenceKeys.VIDEO_CODEC), settings.videoCodec, Storage.PrefsType.SCRCPY)
        Storage.putInt(globalKey(ScrcpyPreferenceKeys.AUDIO_CODEC), settings.audioCodec, Storage.PrefsType.SCRCPY)
        Storage.putInt(globalKey("display_orientation"), settings.displayOrientation, Storage.PrefsType.SCRCPY)
        Storage.putString(globalKey(ScrcpyPreferenceKeys.MAX_FPS_INPUT), settings.frameRate, Storage.PrefsType.SCRCPY)
        Storage.putString(globalKey("crop"), settings.crop, Storage.PrefsType.SCRCPY)
        Storage.putString(globalKey(ScrcpyPreferenceKeys.DISPLAY_ID), settings.display, Storage.PrefsType.SCRCPY)
        Storage.putString(globalKey("virtual_display_size"), settings.virtualDisplaySize, Storage.PrefsType.SCRCPY)
        Storage.putBoolean(globalKey("virtual_display_enabled"), settings.virtualDisplayEnabled, Storage.PrefsType.SCRCPY)
        Storage.putString(globalKey("custom_arguments"), settings.customArguments, Storage.PrefsType.SCRCPY)
        Storage.putBoolean(globalKey(ScrcpyPreferenceKeys.NO_VIDEO), settings.disableVideoForwarding, Storage.PrefsType.SCRCPY)
        Storage.putString(globalKey("audio_output_mode"), settings.audioOutputMode.name, Storage.PrefsType.SCRCPY)
        Storage.putBoolean(globalKey("forward_microphone"), settings.forwardMicrophone, Storage.PrefsType.SCRCPY)

        if (deviceUuid.isNullOrBlank()) return
        Storage.putBoolean(unlockKey(deviceUuid, "before_launch"), settings.unlockBeforeLaunch, Storage.PrefsType.SCRCPY)
        Storage.putInt(unlockKey(deviceUuid, "timeout"), settings.unlockTimeout, Storage.PrefsType.SCRCPY)
        Storage.putString(unlockKey(deviceUuid, "commands"), settings.unlockCommands, Storage.PrefsType.SCRCPY)
        Storage.putString(unlockKey(deviceUuid, "device_preference"), settings.devicePreference.name, Storage.PrefsType.SCRCPY)
        Storage.putBoolean(unlockKey(deviceUuid, "adb_tcpip_mode"), settings.adbTcpipModeEnabled, Storage.PrefsType.SCRCPY)
    }
}
