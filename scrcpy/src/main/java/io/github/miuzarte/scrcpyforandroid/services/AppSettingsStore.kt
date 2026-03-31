package io.github.miuzarte.scrcpyforandroid.services

import android.content.Context
import notifyrelay.data.StorageManager
import notifyrelay.data.config.ScrcpyDefaults
import notifyrelay.data.config.ScrcpyPreferenceKeys

internal data class MainSettings(
    val audioEnabled: Boolean = ScrcpyDefaults.AUDIO_ENABLED,
    val audioCodec: String = ScrcpyDefaults.AUDIO_CODEC,
    val videoCodec: String = ScrcpyDefaults.VIDEO_CODEC,
    val fullscreenDebugInfoEnabled: Boolean = ScrcpyDefaults.FULLSCREEN_DEBUG_INFO,
    val showFullscreenVirtualButtons: Boolean = ScrcpyDefaults.SHOW_FULLSCREEN_VIRTUAL_BUTTONS,
    val keepScreenOnWhenStreamingEnabled: Boolean = ScrcpyDefaults.KEEP_SCREEN_ON_WHEN_STREAMING,
    val virtualButtonsLayout: String = ScrcpyDefaults.VIRTUAL_BUTTONS_LAYOUT,
    val customServerUri: String? = ScrcpyDefaults.CUSTOM_SERVER_URI,
    val serverRemotePath: String = ScrcpyDefaults.SERVER_REMOTE_PATH_INPUT,
    val adbKeyName: String = ScrcpyDefaults.ADB_KEY_NAME_INPUT,
    val adbPairingAutoDiscoverOnDialogOpen: Boolean =
        ScrcpyDefaults.ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN,
    val adbAutoReconnectPairedDevice: Boolean = ScrcpyDefaults.ADB_AUTO_RECONNECT_PAIRED_DEVICE,
    val adbMdnsLanDiscoveryEnabled: Boolean = ScrcpyDefaults.ADB_MDNS_LAN_DISCOVERY,
)

internal data class DevicePageSettings(
    val quickConnectInput: String = ScrcpyDefaults.QUICK_CONNECT_INPUT,
    val pairHost: String = ScrcpyDefaults.PAIR_HOST,
    val pairPort: String = ScrcpyDefaults.PAIR_PORT,
    val pairCode: String = ScrcpyDefaults.PAIR_CODE,
    val audioBitRateKbps: Int = ScrcpyDefaults.AUDIO_BIT_RATE_KBPS,
    val audioBitRateInput: String = ScrcpyDefaults.AUDIO_BIT_RATE_INPUT,
    val videoBitRateMbps: Float = ScrcpyDefaults.VIDEO_BIT_RATE_MBPS,
    val videoBitRateInput: String = ScrcpyDefaults.VIDEO_BIT_RATE_INPUT,
    val turnScreenOff: Boolean = ScrcpyDefaults.TURN_SCREEN_OFF,
    val noControl: Boolean = ScrcpyDefaults.NO_CONTROL,
    val noVideo: Boolean = ScrcpyDefaults.NO_VIDEO,
    val videoSourcePreset: String = ScrcpyDefaults.VIDEO_SOURCE_PRESET,
    val displayIdInput: String = ScrcpyDefaults.DISPLAY_ID,
    val cameraIdInput: String = ScrcpyDefaults.CAMERA_ID,
    val cameraFacingPreset: String = ScrcpyDefaults.CAMERA_FACING_PRESET,
    val cameraSizePreset: String = ScrcpyDefaults.CAMERA_SIZE_PRESET,
    val cameraSizeCustom: String = ScrcpyDefaults.CAMERA_SIZE_CUSTOM,
    val cameraAr: String = ScrcpyDefaults.CAMERA_AR,
    val cameraFps: String = ScrcpyDefaults.CAMERA_FPS,
    val cameraHighSpeed: Boolean = ScrcpyDefaults.CAMERA_HIGH_SPEED,
    val audioSourcePreset: String = ScrcpyDefaults.AUDIO_SOURCE_PRESET,
    val audioSourceCustom: String = ScrcpyDefaults.AUDIO_SOURCE_CUSTOM,
    val audioDup: Boolean = ScrcpyDefaults.AUDIO_DUP,
    val noAudioPlayback: Boolean = ScrcpyDefaults.NO_AUDIO_PLAYBACK,
    val requireAudio: Boolean = ScrcpyDefaults.REQUIRE_AUDIO,
    val maxSizeInput: String = ScrcpyDefaults.MAX_SIZE_INPUT,
    val maxFpsInput: String = ScrcpyDefaults.MAX_FPS_INPUT,
    val videoEncoder: String = ScrcpyDefaults.VIDEO_ENCODER,
    val videoCodecOptions: String = ScrcpyDefaults.VIDEO_CODEC_OPTION,
    val audioEncoder: String = ScrcpyDefaults.AUDIO_ENCODER,
    val audioCodecOptions: String = ScrcpyDefaults.AUDIO_CODEC_OPTION,
    val newDisplayWidth: String = ScrcpyDefaults.NEW_DISPLAY_WIDTH,
    val newDisplayHeight: String = ScrcpyDefaults.NEW_DISPLAY_HEIGHT,
    val newDisplayDpi: String = ScrcpyDefaults.NEW_DISPLAY_DPI,
    val cropWidth: String = ScrcpyDefaults.CROP_WIDTH,
    val cropHeight: String = ScrcpyDefaults.CROP_HEIGHT,
    val cropX: String = ScrcpyDefaults.CROP_X,
    val cropY: String = ScrcpyDefaults.CROP_Y,
)

internal fun loadMainSettings(context: Context): MainSettings {
    return MainSettings(
        audioEnabled = StorageManager.getBoolean(
            context,
            ScrcpyPreferenceKeys.AUDIO_ENABLED,
            ScrcpyDefaults.AUDIO_ENABLED,
            StorageManager.PrefsType.SCRCPY,
        ),
        audioCodec = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.AUDIO_CODEC,
            ScrcpyDefaults.AUDIO_CODEC,
            StorageManager.PrefsType.SCRCPY,
        ).ifBlank { ScrcpyDefaults.AUDIO_CODEC },
        videoCodec = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.VIDEO_CODEC,
            ScrcpyDefaults.VIDEO_CODEC,
            StorageManager.PrefsType.SCRCPY,
        ).ifBlank { ScrcpyDefaults.VIDEO_CODEC },
        fullscreenDebugInfoEnabled = StorageManager.getBoolean(
            context,
            ScrcpyPreferenceKeys.FULLSCREEN_DEBUG_INFO,
            ScrcpyDefaults.FULLSCREEN_DEBUG_INFO,
            StorageManager.PrefsType.SCRCPY,
        ),
        showFullscreenVirtualButtons = StorageManager.getBoolean(
            context,
            ScrcpyPreferenceKeys.SHOW_FULLSCREEN_VIRTUAL_BUTTONS,
            ScrcpyDefaults.SHOW_FULLSCREEN_VIRTUAL_BUTTONS,
            StorageManager.PrefsType.SCRCPY,
        ),
        keepScreenOnWhenStreamingEnabled = StorageManager.getBoolean(
            context,
            ScrcpyPreferenceKeys.KEEP_SCREEN_ON_WHEN_STREAMING,
            ScrcpyDefaults.KEEP_SCREEN_ON_WHEN_STREAMING,
            StorageManager.PrefsType.SCRCPY,
        ),
        virtualButtonsLayout = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.VIRTUAL_BUTTONS_LAYOUT,
            ScrcpyDefaults.VIRTUAL_BUTTONS_LAYOUT,
            StorageManager.PrefsType.SCRCPY,
        ).ifBlank { ScrcpyDefaults.VIRTUAL_BUTTONS_LAYOUT },
        customServerUri = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.CUSTOM_SERVER_URI,
            ScrcpyDefaults.CUSTOM_SERVER_URI,
            StorageManager.PrefsType.SCRCPY,
        ).ifBlank { null },
        serverRemotePath = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.SERVER_REMOTE_PATH,
            ScrcpyDefaults.SERVER_REMOTE_PATH_INPUT,
            StorageManager.PrefsType.SCRCPY,
        ),
        adbKeyName = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.ADB_KEY_NAME,
            ScrcpyDefaults.ADB_KEY_NAME_INPUT,
            StorageManager.PrefsType.SCRCPY,
        ),
        adbPairingAutoDiscoverOnDialogOpen = StorageManager.getBoolean(
            context,
            ScrcpyPreferenceKeys.ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN,
            ScrcpyDefaults.ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN,
            StorageManager.PrefsType.SCRCPY,
        ),
        adbAutoReconnectPairedDevice = StorageManager.getBoolean(
            context,
            ScrcpyPreferenceKeys.ADB_AUTO_RECONNECT_PAIRED_DEVICE,
            ScrcpyDefaults.ADB_AUTO_RECONNECT_PAIRED_DEVICE,
            StorageManager.PrefsType.SCRCPY,
        ),
        adbMdnsLanDiscoveryEnabled = StorageManager.getBoolean(
            context,
            ScrcpyPreferenceKeys.ADB_MDNS_LAN_DISCOVERY,
            ScrcpyDefaults.ADB_MDNS_LAN_DISCOVERY,
            StorageManager.PrefsType.SCRCPY,
        ),
    )
}

internal fun saveMainSettings(context: Context, settings: MainSettings) {
    StorageManager.putBoolean(
        context,
        ScrcpyPreferenceKeys.AUDIO_ENABLED,
        settings.audioEnabled,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.AUDIO_CODEC,
        settings.audioCodec,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.VIDEO_CODEC,
        settings.videoCodec,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putBoolean(
        context,
        ScrcpyPreferenceKeys.FULLSCREEN_DEBUG_INFO,
        settings.fullscreenDebugInfoEnabled,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putBoolean(
        context,
        ScrcpyPreferenceKeys.SHOW_FULLSCREEN_VIRTUAL_BUTTONS,
        settings.showFullscreenVirtualButtons,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putBoolean(
        context,
        ScrcpyPreferenceKeys.KEEP_SCREEN_ON_WHEN_STREAMING,
        settings.keepScreenOnWhenStreamingEnabled,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.VIRTUAL_BUTTONS_LAYOUT,
        settings.virtualButtonsLayout,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.CUSTOM_SERVER_URI,
        settings.customServerUri ?: "",
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.SERVER_REMOTE_PATH,
        settings.serverRemotePath,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.ADB_KEY_NAME,
        settings.adbKeyName,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putBoolean(
        context,
        ScrcpyPreferenceKeys.ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN,
        settings.adbPairingAutoDiscoverOnDialogOpen,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putBoolean(
        context,
        ScrcpyPreferenceKeys.ADB_AUTO_RECONNECT_PAIRED_DEVICE,
        settings.adbAutoReconnectPairedDevice,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putBoolean(
        context,
        ScrcpyPreferenceKeys.ADB_MDNS_LAN_DISCOVERY,
        settings.adbMdnsLanDiscoveryEnabled,
        StorageManager.PrefsType.SCRCPY,
    )
}

internal fun loadDevicePageSettings(context: Context): DevicePageSettings {
    val audioBitRateKbps = StorageManager.getInt(
        context,
        ScrcpyPreferenceKeys.AUDIO_BIT_RATE_KBPS,
        ScrcpyDefaults.AUDIO_BIT_RATE_KBPS,
        StorageManager.PrefsType.SCRCPY,
    )
    return DevicePageSettings(
        quickConnectInput = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.QUICK_CONNECT_INPUT,
            ScrcpyDefaults.QUICK_CONNECT_INPUT,
            StorageManager.PrefsType.SCRCPY,
        ),
        pairHost = ScrcpyDefaults.PAIR_HOST,
        pairPort = ScrcpyDefaults.PAIR_PORT,
        pairCode = ScrcpyDefaults.PAIR_CODE,
        audioBitRateKbps = audioBitRateKbps,
        audioBitRateInput = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.AUDIO_BIT_RATE_INPUT,
            ScrcpyDefaults.AUDIO_BIT_RATE_INPUT,
            StorageManager.PrefsType.SCRCPY,
        ).ifBlank { audioBitRateKbps.toString() },
        videoBitRateMbps = StorageManager.getFloat(
            context,
            ScrcpyPreferenceKeys.VIDEO_BIT_RATE_MBPS,
            ScrcpyDefaults.VIDEO_BIT_RATE_MBPS,
            StorageManager.PrefsType.SCRCPY,
        ),
        videoBitRateInput = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.VIDEO_BIT_RATE_INPUT,
            ScrcpyDefaults.VIDEO_BIT_RATE_INPUT,
            StorageManager.PrefsType.SCRCPY,
        ).ifBlank { ScrcpyDefaults.VIDEO_BIT_RATE_INPUT },
        turnScreenOff = StorageManager.getBoolean(
            context,
            ScrcpyPreferenceKeys.TURN_SCREEN_OFF,
            ScrcpyDefaults.TURN_SCREEN_OFF,
            StorageManager.PrefsType.SCRCPY,
        ),
        noControl = StorageManager.getBoolean(
            context,
            ScrcpyPreferenceKeys.NO_CONTROL,
            ScrcpyDefaults.NO_CONTROL,
            StorageManager.PrefsType.SCRCPY,
        ),
        noVideo = StorageManager.getBoolean(
            context,
            ScrcpyPreferenceKeys.NO_VIDEO,
            ScrcpyDefaults.NO_VIDEO,
            StorageManager.PrefsType.SCRCPY,
        ),
        videoSourcePreset = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.VIDEO_SOURCE_PRESET,
            ScrcpyDefaults.VIDEO_SOURCE_PRESET,
            StorageManager.PrefsType.SCRCPY,
        ).ifBlank { ScrcpyDefaults.VIDEO_SOURCE_PRESET },
        displayIdInput = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.DISPLAY_ID,
            ScrcpyDefaults.DISPLAY_ID,
            StorageManager.PrefsType.SCRCPY,
        ),
        cameraIdInput = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.CAMERA_ID,
            ScrcpyDefaults.CAMERA_ID,
            StorageManager.PrefsType.SCRCPY,
        ),
        cameraFacingPreset = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.CAMERA_FACING_PRESET,
            ScrcpyDefaults.CAMERA_FACING_PRESET,
            StorageManager.PrefsType.SCRCPY,
        ),
        cameraSizePreset = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.CAMERA_SIZE_PRESET,
            ScrcpyDefaults.CAMERA_SIZE_PRESET,
            StorageManager.PrefsType.SCRCPY,
        ),
        cameraSizeCustom = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.CAMERA_SIZE_CUSTOM,
            ScrcpyDefaults.CAMERA_SIZE_CUSTOM,
            StorageManager.PrefsType.SCRCPY,
        ),
        cameraAr = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.CAMERA_AR,
            ScrcpyDefaults.CAMERA_AR,
            StorageManager.PrefsType.SCRCPY,
        ),
        cameraFps = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.CAMERA_FPS,
            ScrcpyDefaults.CAMERA_FPS,
            StorageManager.PrefsType.SCRCPY,
        ),
        cameraHighSpeed = StorageManager.getBoolean(
            context,
            ScrcpyPreferenceKeys.CAMERA_HIGH_SPEED,
            ScrcpyDefaults.CAMERA_HIGH_SPEED,
            StorageManager.PrefsType.SCRCPY,
        ),
        audioSourcePreset = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.AUDIO_SOURCE_PRESET,
            ScrcpyDefaults.AUDIO_SOURCE_PRESET,
            StorageManager.PrefsType.SCRCPY,
        ).ifBlank { ScrcpyDefaults.AUDIO_SOURCE_PRESET },
        audioSourceCustom = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.AUDIO_SOURCE_CUSTOM,
            ScrcpyDefaults.AUDIO_SOURCE_CUSTOM,
            StorageManager.PrefsType.SCRCPY,
        ),
        audioDup = StorageManager.getBoolean(
            context,
            ScrcpyPreferenceKeys.AUDIO_DUP,
            ScrcpyDefaults.AUDIO_DUP,
            StorageManager.PrefsType.SCRCPY,
        ),
        noAudioPlayback = StorageManager.getBoolean(
            context,
            ScrcpyPreferenceKeys.NO_AUDIO_PLAYBACK,
            ScrcpyDefaults.NO_AUDIO_PLAYBACK,
            StorageManager.PrefsType.SCRCPY,
        ),
        requireAudio = StorageManager.getBoolean(
            context,
            ScrcpyPreferenceKeys.REQUIRE_AUDIO,
            ScrcpyDefaults.REQUIRE_AUDIO,
            StorageManager.PrefsType.SCRCPY,
        ),
        maxSizeInput = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.MAX_SIZE_INPUT,
            ScrcpyDefaults.MAX_SIZE_INPUT,
            StorageManager.PrefsType.SCRCPY,
        ),
        maxFpsInput = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.MAX_FPS_INPUT,
            ScrcpyDefaults.MAX_FPS_INPUT,
            StorageManager.PrefsType.SCRCPY,
        ),
        videoEncoder = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.VIDEO_ENCODER,
            ScrcpyDefaults.VIDEO_ENCODER,
            StorageManager.PrefsType.SCRCPY,
        ),
        videoCodecOptions = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.VIDEO_CODEC_OPTION,
            ScrcpyDefaults.VIDEO_CODEC_OPTION,
            StorageManager.PrefsType.SCRCPY,
        ),
        audioEncoder = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.AUDIO_ENCODER,
            ScrcpyDefaults.AUDIO_ENCODER,
            StorageManager.PrefsType.SCRCPY,
        ),
        audioCodecOptions = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.AUDIO_CODEC_OPTION,
            ScrcpyDefaults.AUDIO_CODEC_OPTION,
            StorageManager.PrefsType.SCRCPY,
        ),
        newDisplayWidth = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.NEW_DISPLAY_WIDTH,
            ScrcpyDefaults.NEW_DISPLAY_WIDTH,
            StorageManager.PrefsType.SCRCPY,
        ),
        newDisplayHeight = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.NEW_DISPLAY_HEIGHT,
            ScrcpyDefaults.NEW_DISPLAY_HEIGHT,
            StorageManager.PrefsType.SCRCPY,
        ),
        newDisplayDpi = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.NEW_DISPLAY_DPI,
            ScrcpyDefaults.NEW_DISPLAY_DPI,
            StorageManager.PrefsType.SCRCPY,
        ),
        cropWidth = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.CROP_WIDTH,
            ScrcpyDefaults.CROP_WIDTH,
            StorageManager.PrefsType.SCRCPY,
        ),
        cropHeight = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.CROP_HEIGHT,
            ScrcpyDefaults.CROP_HEIGHT,
            StorageManager.PrefsType.SCRCPY,
        ),
        cropX = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.CROP_X,
            ScrcpyDefaults.CROP_X,
            StorageManager.PrefsType.SCRCPY,
        ),
        cropY = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.CROP_Y,
            ScrcpyDefaults.CROP_Y,
            StorageManager.PrefsType.SCRCPY,
        ),
    )
}

internal fun saveDevicePageSettings(context: Context, settings: DevicePageSettings) {
    StorageManager.remove(
        context,
        ScrcpyPreferenceKeys.PAIR_HOST,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.remove(
        context,
        ScrcpyPreferenceKeys.PAIR_PORT,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.remove(
        context,
        ScrcpyPreferenceKeys.PAIR_CODE,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.QUICK_CONNECT_INPUT,
        settings.quickConnectInput,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putInt(
        context,
        ScrcpyPreferenceKeys.AUDIO_BIT_RATE_KBPS,
        settings.audioBitRateKbps,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.AUDIO_BIT_RATE_INPUT,
        settings.audioBitRateInput,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putFloat(
        context,
        ScrcpyPreferenceKeys.VIDEO_BIT_RATE_MBPS,
        settings.videoBitRateMbps,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.VIDEO_BIT_RATE_INPUT,
        settings.videoBitRateInput,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putBoolean(
        context,
        ScrcpyPreferenceKeys.TURN_SCREEN_OFF,
        settings.turnScreenOff,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putBoolean(
        context,
        ScrcpyPreferenceKeys.NO_CONTROL,
        settings.noControl,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putBoolean(
        context,
        ScrcpyPreferenceKeys.NO_VIDEO,
        settings.noVideo,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.VIDEO_SOURCE_PRESET,
        settings.videoSourcePreset,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.DISPLAY_ID,
        settings.displayIdInput,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.CAMERA_ID,
        settings.cameraIdInput,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.CAMERA_FACING_PRESET,
        settings.cameraFacingPreset,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.CAMERA_SIZE_PRESET,
        settings.cameraSizePreset,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.CAMERA_SIZE_CUSTOM,
        settings.cameraSizeCustom,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.CAMERA_AR,
        settings.cameraAr,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.CAMERA_FPS,
        settings.cameraFps,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putBoolean(
        context,
        ScrcpyPreferenceKeys.CAMERA_HIGH_SPEED,
        settings.cameraHighSpeed,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.AUDIO_SOURCE_PRESET,
        settings.audioSourcePreset,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.AUDIO_SOURCE_CUSTOM,
        settings.audioSourceCustom,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putBoolean(
        context,
        ScrcpyPreferenceKeys.AUDIO_DUP,
        settings.audioDup,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putBoolean(
        context,
        ScrcpyPreferenceKeys.NO_AUDIO_PLAYBACK,
        settings.noAudioPlayback,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putBoolean(
        context,
        ScrcpyPreferenceKeys.REQUIRE_AUDIO,
        settings.requireAudio,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.MAX_SIZE_INPUT,
        settings.maxSizeInput,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.MAX_FPS_INPUT,
        settings.maxFpsInput,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.VIDEO_ENCODER,
        settings.videoEncoder,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.VIDEO_CODEC_OPTION,
        settings.videoCodecOptions,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.AUDIO_ENCODER,
        settings.audioEncoder,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.AUDIO_CODEC_OPTION,
        settings.audioCodecOptions,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.NEW_DISPLAY_WIDTH,
        settings.newDisplayWidth,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.NEW_DISPLAY_HEIGHT,
        settings.newDisplayHeight,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.NEW_DISPLAY_DPI,
        settings.newDisplayDpi,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.CROP_WIDTH,
        settings.cropWidth,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.CROP_HEIGHT,
        settings.cropHeight,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.CROP_X,
        settings.cropX,
        StorageManager.PrefsType.SCRCPY,
    )
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.CROP_Y,
        settings.cropY,
        StorageManager.PrefsType.SCRCPY,
    )
}
