package io.github.miuzarte.scrcpyforandroid.services

import android.content.Context
import androidx.core.content.edit
import io.github.miuzarte.scrcpyforandroid.constants.AppDefaults
import io.github.miuzarte.scrcpyforandroid.constants.AppPreferenceKeys

internal data class MainSettings(
    val audioEnabled: Boolean = AppDefaults.AUDIO_ENABLED,
    val audioCodec: String = AppDefaults.AUDIO_CODEC,
    val videoCodec: String = AppDefaults.VIDEO_CODEC,
    val themeBaseIndex: Int = AppDefaults.THEME_BASE_INDEX,
    val monetEnabled: Boolean = AppDefaults.MONET,
    val fullscreenDebugInfoEnabled: Boolean = AppDefaults.FULLSCREEN_DEBUG_INFO,
    val showFullscreenVirtualButtons: Boolean = AppDefaults.SHOW_FULLSCREEN_VIRTUAL_BUTTONS,
    val showPreviewVirtualButtonText: Boolean = AppDefaults.PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT,
    val keepScreenOnWhenStreamingEnabled: Boolean = AppDefaults.KEEP_SCREEN_ON_WHEN_STREAMING,
    val devicePreviewCardHeightDp: Int = AppDefaults.DEVICE_PREVIEW_CARD_HEIGHT_DP,
    val virtualButtonsLayout: String = AppDefaults.VIRTUAL_BUTTONS_LAYOUT,
    val customServerUri: String? = AppDefaults.CUSTOM_SERVER_URI,
    val serverRemotePath: String = AppDefaults.SERVER_REMOTE_PATH_INPUT,
    val adbKeyName: String = AppDefaults.ADB_KEY_NAME_INPUT,
    val adbPairingAutoDiscoverOnDialogOpen: Boolean =
        AppDefaults.ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN,
    val adbAutoReconnectPairedDevice: Boolean = AppDefaults.ADB_AUTO_RECONNECT_PAIRED_DEVICE,
    val adbMdnsLanDiscoveryEnabled: Boolean = AppDefaults.ADB_MDNS_LAN_DISCOVERY,
)

internal data class DevicePageSettings(
    val quickConnectInput: String = AppDefaults.QUICK_CONNECT_INPUT,
    val pairHost: String = AppDefaults.PAIR_HOST,
    val pairPort: String = AppDefaults.PAIR_PORT,
    val pairCode: String = AppDefaults.PAIR_CODE,
    val audioBitRateKbps: Int = AppDefaults.AUDIO_BIT_RATE_KBPS,
    val audioBitRateInput: String = AppDefaults.AUDIO_BIT_RATE_INPUT,
    val videoBitRateMbps: Float = AppDefaults.VIDEO_BIT_RATE_MBPS,
    val videoBitRateInput: String = AppDefaults.VIDEO_BIT_RATE_INPUT,
    val turnScreenOff: Boolean = AppDefaults.TURN_SCREEN_OFF,
    val noControl: Boolean = AppDefaults.NO_CONTROL,
    val noVideo: Boolean = AppDefaults.NO_VIDEO,
    val videoSourcePreset: String = AppDefaults.VIDEO_SOURCE_PRESET,
    val displayIdInput: String = AppDefaults.DISPLAY_ID,
    val cameraIdInput: String = AppDefaults.CAMERA_ID,
    val cameraFacingPreset: String = AppDefaults.CAMERA_FACING_PRESET,
    val cameraSizePreset: String = AppDefaults.CAMERA_SIZE_PRESET,
    val cameraSizeCustom: String = AppDefaults.CAMERA_SIZE_CUSTOM,
    val cameraAr: String = AppDefaults.CAMERA_AR,
    val cameraFps: String = AppDefaults.CAMERA_FPS,
    val cameraHighSpeed: Boolean = AppDefaults.CAMERA_HIGH_SPEED,
    val audioSourcePreset: String = AppDefaults.AUDIO_SOURCE_PRESET,
    val audioSourceCustom: String = AppDefaults.AUDIO_SOURCE_CUSTOM,
    val audioDup: Boolean = AppDefaults.AUDIO_DUP,
    val noAudioPlayback: Boolean = AppDefaults.NO_AUDIO_PLAYBACK,
    val requireAudio: Boolean = AppDefaults.REQUIRE_AUDIO,
    val maxSizeInput: String = AppDefaults.MAX_SIZE_INPUT,
    val maxFpsInput: String = AppDefaults.MAX_FPS_INPUT,
    val videoEncoder: String = AppDefaults.VIDEO_ENCODER,
    val videoCodecOptions: String = AppDefaults.VIDEO_CODEC_OPTION,
    val audioEncoder: String = AppDefaults.AUDIO_ENCODER,
    val audioCodecOptions: String = AppDefaults.AUDIO_CODEC_OPTION,
    val newDisplayWidth: String = AppDefaults.NEW_DISPLAY_WIDTH,
    val newDisplayHeight: String = AppDefaults.NEW_DISPLAY_HEIGHT,
    val newDisplayDpi: String = AppDefaults.NEW_DISPLAY_DPI,
    val cropWidth: String = AppDefaults.CROP_WIDTH,
    val cropHeight: String = AppDefaults.CROP_HEIGHT,
    val cropX: String = AppDefaults.CROP_X,
    val cropY: String = AppDefaults.CROP_Y,
)

internal fun loadMainSettings(context: Context): MainSettings {
    val prefs = context.getSharedPreferences(
        AppPreferenceKeys.PREFS_NAME,
        Context.MODE_PRIVATE,
    )
    return MainSettings(
        audioEnabled = prefs.getBoolean(
            AppPreferenceKeys.AUDIO_ENABLED,
            AppDefaults.AUDIO_ENABLED,
        ),
        audioCodec = prefs.getString(
            AppPreferenceKeys.AUDIO_CODEC,
            AppDefaults.AUDIO_CODEC,
        ).orEmpty().ifBlank { AppDefaults.AUDIO_CODEC },
        videoCodec = prefs.getString(
            AppPreferenceKeys.VIDEO_CODEC,
            AppDefaults.VIDEO_CODEC,
        ).orEmpty().ifBlank { AppDefaults.VIDEO_CODEC },
        themeBaseIndex = prefs.getInt(
            AppPreferenceKeys.THEME_BASE_INDEX,
            AppDefaults.THEME_BASE_INDEX,
        ),
        monetEnabled = prefs.getBoolean(
            AppPreferenceKeys.MONET,
            AppDefaults.MONET,
        ),
        fullscreenDebugInfoEnabled = prefs.getBoolean(
            AppPreferenceKeys.FULLSCREEN_DEBUG_INFO,
            AppDefaults.FULLSCREEN_DEBUG_INFO,
        ),
        showFullscreenVirtualButtons = prefs.getBoolean(
            AppPreferenceKeys.SHOW_FULLSCREEN_VIRTUAL_BUTTONS,
            AppDefaults.SHOW_FULLSCREEN_VIRTUAL_BUTTONS,
        ),
        showPreviewVirtualButtonText = prefs.getBoolean(
            AppPreferenceKeys.PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT,
            AppDefaults.PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT,
        ),
        keepScreenOnWhenStreamingEnabled = prefs.getBoolean(
            AppPreferenceKeys.KEEP_SCREEN_ON_WHEN_STREAMING,
            AppDefaults.KEEP_SCREEN_ON_WHEN_STREAMING,
        ),
        devicePreviewCardHeightDp = prefs.getInt(
            AppPreferenceKeys.DEVICE_PREVIEW_CARD_HEIGHT_DP,
            AppDefaults.DEVICE_PREVIEW_CARD_HEIGHT_DP,
        ).coerceAtLeast(120),
        virtualButtonsLayout = prefs.getString(
            AppPreferenceKeys.VIRTUAL_BUTTONS_LAYOUT,
            AppDefaults.VIRTUAL_BUTTONS_LAYOUT,
        ).orEmpty().ifBlank { AppDefaults.VIRTUAL_BUTTONS_LAYOUT },
        customServerUri = prefs.getString(
            AppPreferenceKeys.CUSTOM_SERVER_URI,
            AppDefaults.CUSTOM_SERVER_URI
        ).orEmpty().ifBlank { null },
        serverRemotePath = prefs.getString(
            AppPreferenceKeys.SERVER_REMOTE_PATH,
            AppDefaults.SERVER_REMOTE_PATH_INPUT,
        ).orEmpty(),
        adbKeyName = prefs.getString(
            AppPreferenceKeys.ADB_KEY_NAME,
            AppDefaults.ADB_KEY_NAME_INPUT,
        ).orEmpty(),
        adbPairingAutoDiscoverOnDialogOpen = prefs.getBoolean(
            AppPreferenceKeys.ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN,
            AppDefaults.ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN,
        ),
        adbAutoReconnectPairedDevice = prefs.getBoolean(
            AppPreferenceKeys.ADB_AUTO_RECONNECT_PAIRED_DEVICE,
            AppDefaults.ADB_AUTO_RECONNECT_PAIRED_DEVICE,
        ),
        adbMdnsLanDiscoveryEnabled = prefs.getBoolean(
            AppPreferenceKeys.ADB_MDNS_LAN_DISCOVERY,
            AppDefaults.ADB_MDNS_LAN_DISCOVERY,
        ),
    )
}

internal fun saveMainSettings(context: Context, settings: MainSettings) {
    context.getSharedPreferences(
        AppPreferenceKeys.PREFS_NAME,
        Context.MODE_PRIVATE,
    ).edit {
        putBoolean(
            AppPreferenceKeys.AUDIO_ENABLED,
            settings.audioEnabled,
        )
            .putString(
                AppPreferenceKeys.AUDIO_CODEC,
                settings.audioCodec,
            )
            .putString(
                AppPreferenceKeys.VIDEO_CODEC,
                settings.videoCodec,
            )
            .putInt(
                AppPreferenceKeys.THEME_BASE_INDEX,
                settings.themeBaseIndex,
            )
            .putBoolean(
                AppPreferenceKeys.MONET,
                settings.monetEnabled,
            )
            .putBoolean(
                AppPreferenceKeys.FULLSCREEN_DEBUG_INFO,
                settings.fullscreenDebugInfoEnabled,
            )
            .putBoolean(
                AppPreferenceKeys.SHOW_FULLSCREEN_VIRTUAL_BUTTONS,
                settings.showFullscreenVirtualButtons,
            )
            .putBoolean(
                AppPreferenceKeys.PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT,
                settings.showPreviewVirtualButtonText,
            )
            .putBoolean(
                AppPreferenceKeys.KEEP_SCREEN_ON_WHEN_STREAMING,
                settings.keepScreenOnWhenStreamingEnabled,
            )
            .putInt(
                AppPreferenceKeys.DEVICE_PREVIEW_CARD_HEIGHT_DP,
                settings.devicePreviewCardHeightDp.coerceAtLeast(120),
            )
            .putString(
                AppPreferenceKeys.VIRTUAL_BUTTONS_LAYOUT,
                settings.virtualButtonsLayout,
            )
            .putString(
                AppPreferenceKeys.CUSTOM_SERVER_URI,
                settings.customServerUri,
            )
            .putString(
                AppPreferenceKeys.SERVER_REMOTE_PATH,
                settings.serverRemotePath,
            )
            .putString(
                AppPreferenceKeys.ADB_KEY_NAME,
                settings.adbKeyName,
            )
            .putBoolean(
                AppPreferenceKeys.ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN,
                settings.adbPairingAutoDiscoverOnDialogOpen,
            )
            .putBoolean(
                AppPreferenceKeys.ADB_AUTO_RECONNECT_PAIRED_DEVICE,
                settings.adbAutoReconnectPairedDevice,
            )
            .putBoolean(
                AppPreferenceKeys.ADB_MDNS_LAN_DISCOVERY,
                settings.adbMdnsLanDiscoveryEnabled,
            )
    }
}

internal fun loadDevicePageSettings(context: Context): DevicePageSettings {
    val prefs = context.getSharedPreferences(
        AppPreferenceKeys.PREFS_NAME,
        Context.MODE_PRIVATE,
    )
    val audioBitRateKbps = prefs.getInt(
        AppPreferenceKeys.AUDIO_BIT_RATE_KBPS,
        AppDefaults.AUDIO_BIT_RATE_KBPS,
    )
    return DevicePageSettings(
        quickConnectInput = prefs.getString(
            AppPreferenceKeys.QUICK_CONNECT_INPUT,
            AppDefaults.QUICK_CONNECT_INPUT,
        ).orEmpty(),
        pairHost = AppDefaults.PAIR_HOST,
        pairPort = AppDefaults.PAIR_PORT,
        pairCode = AppDefaults.PAIR_CODE,
        audioBitRateKbps = audioBitRateKbps,
        audioBitRateInput = prefs.getString(
            AppPreferenceKeys.AUDIO_BIT_RATE_INPUT,
            AppDefaults.AUDIO_BIT_RATE_INPUT,
        ).orEmpty().ifBlank { audioBitRateKbps.toString() },
        videoBitRateMbps = prefs.getFloat(
            AppPreferenceKeys.VIDEO_BIT_RATE_MBPS,
            AppDefaults.VIDEO_BIT_RATE_MBPS,
        ),
        videoBitRateInput = prefs.getString(
            AppPreferenceKeys.VIDEO_BIT_RATE_INPUT,
            AppDefaults.VIDEO_BIT_RATE_INPUT
        ).orEmpty().ifBlank { AppDefaults.VIDEO_BIT_RATE_INPUT },
        turnScreenOff = prefs.getBoolean(
            AppPreferenceKeys.TURN_SCREEN_OFF,
            AppDefaults.TURN_SCREEN_OFF,
        ),
        noControl = prefs.getBoolean(
            AppPreferenceKeys.NO_CONTROL,
            AppDefaults.NO_CONTROL,
        ),
        noVideo = prefs.getBoolean(
            AppPreferenceKeys.NO_VIDEO,
            AppDefaults.NO_VIDEO,
        ),
        videoSourcePreset = prefs.getString(
            AppPreferenceKeys.VIDEO_SOURCE_PRESET,
            AppDefaults.VIDEO_SOURCE_PRESET,
        ).orEmpty().ifBlank { AppDefaults.VIDEO_SOURCE_PRESET },
        displayIdInput = prefs.getString(
            AppPreferenceKeys.DISPLAY_ID,
            AppDefaults.DISPLAY_ID,
        )
            .orEmpty(),
        cameraIdInput = prefs.getString(
            AppPreferenceKeys.CAMERA_ID,
            AppDefaults.CAMERA_ID,
        )
            .orEmpty(),
        cameraFacingPreset = prefs.getString(
            AppPreferenceKeys.CAMERA_FACING_PRESET,
            AppDefaults.CAMERA_FACING_PRESET,
        ).orEmpty(),
        cameraSizePreset = prefs.getString(
            AppPreferenceKeys.CAMERA_SIZE_PRESET,
            AppDefaults.CAMERA_SIZE_PRESET,
        ).orEmpty(),
        cameraSizeCustom = prefs.getString(
            AppPreferenceKeys.CAMERA_SIZE_CUSTOM,
            AppDefaults.CAMERA_SIZE_CUSTOM,
        ).orEmpty(),
        cameraAr = prefs.getString(
            AppPreferenceKeys.CAMERA_AR,
            AppDefaults.CAMERA_AR,
        ).orEmpty(),
        cameraFps = prefs.getString(
            AppPreferenceKeys.CAMERA_FPS,
            AppDefaults.CAMERA_FPS,
        ).orEmpty(),
        cameraHighSpeed = prefs.getBoolean(
            AppPreferenceKeys.CAMERA_HIGH_SPEED,
            AppDefaults.CAMERA_HIGH_SPEED,
        ),
        audioSourcePreset = prefs.getString(
            AppPreferenceKeys.AUDIO_SOURCE_PRESET,
            AppDefaults.AUDIO_SOURCE_PRESET,
        ).orEmpty().ifBlank { AppDefaults.AUDIO_SOURCE_PRESET },
        audioSourceCustom = prefs.getString(
            AppPreferenceKeys.AUDIO_SOURCE_CUSTOM,
            AppDefaults.AUDIO_SOURCE_CUSTOM,
        ).orEmpty(),
        audioDup = prefs.getBoolean(
            AppPreferenceKeys.AUDIO_DUP,
            AppDefaults.AUDIO_DUP,
        ),
        noAudioPlayback = prefs.getBoolean(
            AppPreferenceKeys.NO_AUDIO_PLAYBACK,
            AppDefaults.NO_AUDIO_PLAYBACK,
        ),
        requireAudio = prefs.getBoolean(
            AppPreferenceKeys.REQUIRE_AUDIO,
            AppDefaults.REQUIRE_AUDIO,
        ),
        maxSizeInput = prefs.getString(
            AppPreferenceKeys.MAX_SIZE_INPUT,
            AppDefaults.MAX_SIZE_INPUT,
        )
            .orEmpty(),
        maxFpsInput = prefs.getString(
            AppPreferenceKeys.MAX_FPS_INPUT,
            AppDefaults.MAX_FPS_INPUT,
        )
            .orEmpty(),
        videoEncoder = prefs.getString(
            AppPreferenceKeys.VIDEO_ENCODER,
            AppDefaults.VIDEO_ENCODER,
        )
            .orEmpty(),
        videoCodecOptions = prefs.getString(
            AppPreferenceKeys.VIDEO_CODEC_OPTION,
            AppDefaults.VIDEO_CODEC_OPTION,
        ).orEmpty(),
        audioEncoder = prefs.getString(
            AppPreferenceKeys.AUDIO_ENCODER,
            AppDefaults.AUDIO_ENCODER,
        ).orEmpty(),
        audioCodecOptions = prefs.getString(
            AppPreferenceKeys.AUDIO_CODEC_OPTION,
            AppDefaults.AUDIO_CODEC_OPTION,
        ).orEmpty(),
        newDisplayWidth = prefs.getString(
            AppPreferenceKeys.NEW_DISPLAY_WIDTH,
            AppDefaults.NEW_DISPLAY_WIDTH,
        ).orEmpty(),
        newDisplayHeight = prefs.getString(
            AppPreferenceKeys.NEW_DISPLAY_HEIGHT,
            AppDefaults.NEW_DISPLAY_HEIGHT,
        ).orEmpty(),
        newDisplayDpi = prefs.getString(
            AppPreferenceKeys.NEW_DISPLAY_DPI,
            AppDefaults.NEW_DISPLAY_DPI,
        ).orEmpty(),
        cropWidth = prefs.getString(
            AppPreferenceKeys.CROP_WIDTH,
            AppDefaults.CROP_WIDTH,
        ).orEmpty(),
        cropHeight = prefs.getString(
            AppPreferenceKeys.CROP_HEIGHT,
            AppDefaults.CROP_HEIGHT,
        ).orEmpty(),
        cropX = prefs.getString(
            AppPreferenceKeys.CROP_X,
            AppDefaults.CROP_X,
        ).orEmpty(),
        cropY = prefs.getString(
            AppPreferenceKeys.CROP_Y,
            AppDefaults.CROP_Y,
        ).orEmpty(),
    )
}

internal fun saveDevicePageSettings(context: Context, settings: DevicePageSettings) {
    context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
        .edit {
            remove(AppPreferenceKeys.PAIR_HOST)
                .remove(AppPreferenceKeys.PAIR_PORT)
                .remove(AppPreferenceKeys.PAIR_CODE)
                .putString(
                    AppPreferenceKeys.QUICK_CONNECT_INPUT,
                    settings.quickConnectInput,
                )
                .putInt(
                    AppPreferenceKeys.AUDIO_BIT_RATE_KBPS,
                    settings.audioBitRateKbps,
                )
                .putString(
                    AppPreferenceKeys.AUDIO_BIT_RATE_INPUT,
                    settings.audioBitRateInput,
                )
                .putFloat(
                    AppPreferenceKeys.VIDEO_BIT_RATE_MBPS,
                    settings.videoBitRateMbps,
                )
                .putString(
                    AppPreferenceKeys.VIDEO_BIT_RATE_INPUT,
                    settings.videoBitRateInput,
                )
                .putBoolean(
                    AppPreferenceKeys.TURN_SCREEN_OFF,
                    settings.turnScreenOff,
                )
                .putBoolean(
                    AppPreferenceKeys.NO_CONTROL,
                    settings.noControl,
                )
                .putBoolean(
                    AppPreferenceKeys.NO_VIDEO,
                    settings.noVideo,
                )
                .putString(
                    AppPreferenceKeys.VIDEO_SOURCE_PRESET,
                    settings.videoSourcePreset,
                )
                .putString(
                    AppPreferenceKeys.DISPLAY_ID,
                    settings.displayIdInput,
                )
                .putString(
                    AppPreferenceKeys.CAMERA_ID,
                    settings.cameraIdInput,
                )
                .putString(
                    AppPreferenceKeys.CAMERA_FACING_PRESET,
                    settings.cameraFacingPreset,
                )
                .putString(
                    AppPreferenceKeys.CAMERA_SIZE_PRESET,
                    settings.cameraSizePreset,
                )
                .putString(
                    AppPreferenceKeys.CAMERA_SIZE_CUSTOM,
                    settings.cameraSizeCustom,
                )
                .putString(
                    AppPreferenceKeys.CAMERA_AR,
                    settings.cameraAr,
                )
                .putString(
                    AppPreferenceKeys.CAMERA_FPS,
                    settings.cameraFps,
                )
                .putBoolean(
                    AppPreferenceKeys.CAMERA_HIGH_SPEED,
                    settings.cameraHighSpeed,
                )
                .putString(
                    AppPreferenceKeys.AUDIO_SOURCE_PRESET,
                    settings.audioSourcePreset,
                )
                .putString(
                    AppPreferenceKeys.AUDIO_SOURCE_CUSTOM,
                    settings.audioSourceCustom,
                )
                .putBoolean(
                    AppPreferenceKeys.AUDIO_DUP,
                    settings.audioDup,
                )
                .putBoolean(
                    AppPreferenceKeys.NO_AUDIO_PLAYBACK,
                    settings.noAudioPlayback,
                )
                .putBoolean(
                    AppPreferenceKeys.REQUIRE_AUDIO,
                    settings.requireAudio,
                )
                .putString(
                    AppPreferenceKeys.MAX_SIZE_INPUT,
                    settings.maxSizeInput,
                )
                .putString(
                    AppPreferenceKeys.MAX_FPS_INPUT,
                    settings.maxFpsInput,
                )
                .putString(
                    AppPreferenceKeys.VIDEO_ENCODER,
                    settings.videoEncoder,
                )
                .putString(
                    AppPreferenceKeys.VIDEO_CODEC_OPTION,
                    settings.videoCodecOptions,
                )
                .putString(
                    AppPreferenceKeys.AUDIO_ENCODER,
                    settings.audioEncoder,
                )
                .putString(
                    AppPreferenceKeys.AUDIO_CODEC_OPTION,
                    settings.audioCodecOptions,
                )
                .putString(
                    AppPreferenceKeys.NEW_DISPLAY_WIDTH,
                    settings.newDisplayWidth,
                )
                .putString(
                    AppPreferenceKeys.NEW_DISPLAY_HEIGHT,
                    settings.newDisplayHeight,
                )
                .putString(
                    AppPreferenceKeys.NEW_DISPLAY_DPI,
                    settings.newDisplayDpi,
                )
                .putString(
                    AppPreferenceKeys.CROP_WIDTH,
                    settings.cropWidth,
                )
                .putString(
                    AppPreferenceKeys.CROP_HEIGHT,
                    settings.cropHeight,
                )
                .putString(
                    AppPreferenceKeys.CROP_X,
                    settings.cropX,
                )
                .putString(
                    AppPreferenceKeys.CROP_Y,
                    settings.cropY,
                )
        }
}
