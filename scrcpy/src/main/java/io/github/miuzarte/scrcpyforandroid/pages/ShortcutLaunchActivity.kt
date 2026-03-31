package io.github.miuzarte.scrcpyforandroid.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.ScrcpySessionInfo
import io.github.miuzarte.scrcpyforandroid.services.DevicePageSettings
import io.github.miuzarte.scrcpyforandroid.services.fetchConnectedDeviceInfo
import io.github.miuzarte.scrcpyforandroid.services.loadDevicePageSettings
import io.github.miuzarte.scrcpyforandroid.services.loadMainSettings
import io.github.miuzarte.scrcpyforandroid.widgets.FullscreenControlScreen
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonActions
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notifyrelay.data.config.ScrcpyDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class ShortcutLaunchActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_DEVICE_IP = "device_ip"
        private const val EXTRA_DEVICE_PORT = "device_port"
        private const val EXTRA_DEVICE_NAME = "device_name"

        private const val EXTRA_VIDEO_BIT_RATE_MBPS = "video_bit_rate_mbps"
        private const val EXTRA_AUDIO_BIT_RATE_KBPS = "audio_bit_rate_kbps"
        private const val EXTRA_VIDEO_CODEC = "video_codec"
        private const val EXTRA_AUDIO_CODEC = "audio_codec"
        private const val EXTRA_AUDIO_ENABLED = "audio_enabled"
        private const val EXTRA_SERVER_REMOTE_PATH = "server_remote_path"
        private const val EXTRA_CUSTOM_SERVER_URI = "custom_server_uri"

        private const val EXTRA_TURN_SCREEN_OFF = "turn_screen_off"
        private const val EXTRA_NO_CONTROL = "no_control"
        private const val EXTRA_NO_VIDEO = "no_video"
        private const val EXTRA_VIDEO_SOURCE_PRESET = "video_source_preset"
        private const val EXTRA_DISPLAY_ID_INPUT = "display_id_input"
        private const val EXTRA_CAMERA_ID_INPUT = "camera_id_input"
        private const val EXTRA_CAMERA_FACING_PRESET = "camera_facing_preset"
        private const val EXTRA_CAMERA_SIZE_PRESET = "camera_size_preset"
        private const val EXTRA_CAMERA_SIZE_CUSTOM = "camera_size_custom"
        private const val EXTRA_CAMERA_AR = "camera_ar"
        private const val EXTRA_CAMERA_FPS = "camera_fps"
        private const val EXTRA_CAMERA_HIGH_SPEED = "camera_high_speed"
        private const val EXTRA_AUDIO_SOURCE_PRESET = "audio_source_preset"
        private const val EXTRA_AUDIO_SOURCE_CUSTOM = "audio_source_custom"
        private const val EXTRA_AUDIO_DUP = "audio_dup"
        private const val EXTRA_NO_AUDIO_PLAYBACK = "no_audio_playback"
        private const val EXTRA_REQUIRE_AUDIO = "require_audio"
        private const val EXTRA_MAX_SIZE_INPUT = "max_size_input"
        private const val EXTRA_MAX_FPS_INPUT = "max_fps_input"
        private const val EXTRA_VIDEO_ENCODER = "video_encoder"
        private const val EXTRA_VIDEO_CODEC_OPTIONS = "video_codec_options"
        private const val EXTRA_AUDIO_ENCODER = "audio_encoder"
        private const val EXTRA_AUDIO_CODEC_OPTIONS = "audio_codec_options"
        private const val EXTRA_NEW_DISPLAY_WIDTH = "new_display_width"
        private const val EXTRA_NEW_DISPLAY_HEIGHT = "new_display_height"
        private const val EXTRA_NEW_DISPLAY_DPI = "new_display_dpi"
        private const val EXTRA_CROP_WIDTH = "crop_width"
        private const val EXTRA_CROP_HEIGHT = "crop_height"
        private const val EXTRA_CROP_X = "crop_x"
        private const val EXTRA_CROP_Y = "crop_y"

        fun startFullscreenControl(context: Context, ip: String, port: Int, name: String) {
            val intent = Intent(context, ShortcutLaunchActivity::class.java).apply {
                putExtra(EXTRA_DEVICE_IP, ip)
                putExtra(EXTRA_DEVICE_PORT, port)
                putExtra(EXTRA_DEVICE_NAME, name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun startFullscreenControl(
            context: Context,
            ip: String,
            port: Int,
            name: String,
            videoBitRateMbps: Float,
            audioBitRateKbps: Int,
            videoCodec: String,
            audioCodec: String,
            audioEnabled: Boolean,
            serverRemotePath: String,
            customServerUri: String?,
            turnScreenOff: Boolean,
            noControl: Boolean,
            noVideo: Boolean,
            videoSourcePreset: String,
            displayIdInput: String,
            cameraIdInput: String,
            cameraFacingPreset: String,
            cameraSizePreset: String,
            cameraSizeCustom: String,
            cameraAr: String,
            cameraFps: String,
            cameraHighSpeed: Boolean,
            audioSourcePreset: String,
            audioSourceCustom: String,
            audioDup: Boolean,
            noAudioPlayback: Boolean,
            requireAudio: Boolean,
            maxSizeInput: String,
            maxFpsInput: String,
            videoEncoder: String,
            videoCodecOptions: String,
            audioEncoder: String,
            audioCodecOptions: String,
            newDisplayWidth: String,
            newDisplayHeight: String,
            newDisplayDpi: String,
            cropWidth: String,
            cropHeight: String,
            cropX: String,
            cropY: String,
        ) {
            val intent = Intent(context, ShortcutLaunchActivity::class.java).apply {
                putExtra(EXTRA_DEVICE_IP, ip)
                putExtra(EXTRA_DEVICE_PORT, port)
                putExtra(EXTRA_DEVICE_NAME, name)
                putExtra(EXTRA_VIDEO_BIT_RATE_MBPS, videoBitRateMbps)
                putExtra(EXTRA_AUDIO_BIT_RATE_KBPS, audioBitRateKbps)
                putExtra(EXTRA_VIDEO_CODEC, videoCodec)
                putExtra(EXTRA_AUDIO_CODEC, audioCodec)
                putExtra(EXTRA_AUDIO_ENABLED, audioEnabled)
                putExtra(EXTRA_SERVER_REMOTE_PATH, serverRemotePath)
                putExtra(EXTRA_CUSTOM_SERVER_URI, customServerUri)
                putExtra(EXTRA_TURN_SCREEN_OFF, turnScreenOff)
                putExtra(EXTRA_NO_CONTROL, noControl)
                putExtra(EXTRA_NO_VIDEO, noVideo)
                putExtra(EXTRA_VIDEO_SOURCE_PRESET, videoSourcePreset)
                putExtra(EXTRA_DISPLAY_ID_INPUT, displayIdInput)
                putExtra(EXTRA_CAMERA_ID_INPUT, cameraIdInput)
                putExtra(EXTRA_CAMERA_FACING_PRESET, cameraFacingPreset)
                putExtra(EXTRA_CAMERA_SIZE_PRESET, cameraSizePreset)
                putExtra(EXTRA_CAMERA_SIZE_CUSTOM, cameraSizeCustom)
                putExtra(EXTRA_CAMERA_AR, cameraAr)
                putExtra(EXTRA_CAMERA_FPS, cameraFps)
                putExtra(EXTRA_CAMERA_HIGH_SPEED, cameraHighSpeed)
                putExtra(EXTRA_AUDIO_SOURCE_PRESET, audioSourcePreset)
                putExtra(EXTRA_AUDIO_SOURCE_CUSTOM, audioSourceCustom)
                putExtra(EXTRA_AUDIO_DUP, audioDup)
                putExtra(EXTRA_NO_AUDIO_PLAYBACK, noAudioPlayback)
                putExtra(EXTRA_REQUIRE_AUDIO, requireAudio)
                putExtra(EXTRA_MAX_SIZE_INPUT, maxSizeInput)
                putExtra(EXTRA_MAX_FPS_INPUT, maxFpsInput)
                putExtra(EXTRA_VIDEO_ENCODER, videoEncoder)
                putExtra(EXTRA_VIDEO_CODEC_OPTIONS, videoCodecOptions)
                putExtra(EXTRA_AUDIO_ENCODER, audioEncoder)
                putExtra(EXTRA_AUDIO_CODEC_OPTIONS, audioCodecOptions)
                putExtra(EXTRA_NEW_DISPLAY_WIDTH, newDisplayWidth)
                putExtra(EXTRA_NEW_DISPLAY_HEIGHT, newDisplayHeight)
                putExtra(EXTRA_NEW_DISPLAY_DPI, newDisplayDpi)
                putExtra(EXTRA_CROP_WIDTH, cropWidth)
                putExtra(EXTRA_CROP_HEIGHT, cropHeight)
                putExtra(EXTRA_CROP_X, cropX)
                putExtra(EXTRA_CROP_Y, cropY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceIp = intent?.getStringExtra(EXTRA_DEVICE_IP)
            ?: intent?.getStringExtra("shortcut_device_ip")
        val devicePort = intent?.getIntExtra(EXTRA_DEVICE_PORT, ScrcpyDefaults.ADB_PORT)
            ?: intent?.getIntExtra("shortcut_device_port", ScrcpyDefaults.ADB_PORT)
            ?: ScrcpyDefaults.ADB_PORT
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME)
            ?: intent?.getStringExtra("shortcut_device_name")
            ?: deviceIp ?: "设备"

        if (deviceIp.isNullOrBlank()) {
            Toast.makeText(this, "无效的设备信息", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val hasExtendedParams = intent?.hasExtra(EXTRA_VIDEO_BIT_RATE_MBPS) == true

        val persistedSettings = loadDevicePageSettings(this)
        val mainSettings = loadMainSettings(this)

        val videoBitRateMbps = if (hasExtendedParams) {
            intent?.getFloatExtra(EXTRA_VIDEO_BIT_RATE_MBPS, ScrcpyDefaults.VIDEO_BIT_RATE_MBPS) ?: ScrcpyDefaults.VIDEO_BIT_RATE_MBPS
        } else {
            persistedSettings.videoBitRateMbps
        }

        val audioBitRateKbps = if (hasExtendedParams) {
            intent?.getIntExtra(EXTRA_AUDIO_BIT_RATE_KBPS, ScrcpyDefaults.AUDIO_BIT_RATE_KBPS) ?: ScrcpyDefaults.AUDIO_BIT_RATE_KBPS
        } else {
            persistedSettings.audioBitRateKbps
        }

        val videoCodec = if (hasExtendedParams) {
            intent?.getStringExtra(EXTRA_VIDEO_CODEC)?.ifBlank { ScrcpyDefaults.VIDEO_CODEC } ?: ScrcpyDefaults.VIDEO_CODEC
        } else {
            mainSettings.videoCodec
        }

        val audioCodec = if (hasExtendedParams) {
            intent?.getStringExtra(EXTRA_AUDIO_CODEC)?.ifBlank { ScrcpyDefaults.AUDIO_CODEC } ?: ScrcpyDefaults.AUDIO_CODEC
        } else {
            mainSettings.audioCodec
        }

        val audioEnabled = if (hasExtendedParams) {
            intent?.getBooleanExtra(EXTRA_AUDIO_ENABLED, ScrcpyDefaults.AUDIO_ENABLED) ?: ScrcpyDefaults.AUDIO_ENABLED
        } else {
            mainSettings.audioEnabled
        }

        val serverRemotePath = if (hasExtendedParams) {
            intent?.getStringExtra(EXTRA_SERVER_REMOTE_PATH)?.ifBlank { ScrcpyDefaults.SERVER_REMOTE_PATH } ?: ScrcpyDefaults.SERVER_REMOTE_PATH
        } else {
            mainSettings.serverRemotePath.ifBlank { ScrcpyDefaults.SERVER_REMOTE_PATH }
        }

        val customServerUri = if (hasExtendedParams) {
            intent?.getStringExtra(EXTRA_CUSTOM_SERVER_URI)?.ifBlank { null }
        } else {
            mainSettings.customServerUri
        }

        val turnScreenOff = intent?.getBooleanExtra(EXTRA_TURN_SCREEN_OFF, ScrcpyDefaults.TURN_SCREEN_OFF) ?: ScrcpyDefaults.TURN_SCREEN_OFF
        val noControl = intent?.getBooleanExtra(EXTRA_NO_CONTROL, ScrcpyDefaults.NO_CONTROL) ?: ScrcpyDefaults.NO_CONTROL
        val noVideo = intent?.getBooleanExtra(EXTRA_NO_VIDEO, ScrcpyDefaults.NO_VIDEO) ?: ScrcpyDefaults.NO_VIDEO
        val videoSourcePreset = intent?.getStringExtra(EXTRA_VIDEO_SOURCE_PRESET)?.ifBlank { ScrcpyDefaults.VIDEO_SOURCE_PRESET } ?: ScrcpyDefaults.VIDEO_SOURCE_PRESET
        val displayIdInput = intent?.getStringExtra(EXTRA_DISPLAY_ID_INPUT)?.ifBlank { ScrcpyDefaults.DISPLAY_ID } ?: ScrcpyDefaults.DISPLAY_ID
        val cameraIdInput = intent?.getStringExtra(EXTRA_CAMERA_ID_INPUT)?.ifBlank { ScrcpyDefaults.CAMERA_ID } ?: ScrcpyDefaults.CAMERA_ID
        val cameraFacingPreset = intent?.getStringExtra(EXTRA_CAMERA_FACING_PRESET)?.ifBlank { ScrcpyDefaults.CAMERA_FACING_PRESET } ?: ScrcpyDefaults.CAMERA_FACING_PRESET
        val cameraSizePreset = intent?.getStringExtra(EXTRA_CAMERA_SIZE_PRESET)?.ifBlank { ScrcpyDefaults.CAMERA_SIZE_PRESET } ?: ScrcpyDefaults.CAMERA_SIZE_PRESET
        val cameraSizeCustom = intent?.getStringExtra(EXTRA_CAMERA_SIZE_CUSTOM)?.ifBlank { ScrcpyDefaults.CAMERA_SIZE_CUSTOM } ?: ScrcpyDefaults.CAMERA_SIZE_CUSTOM
        val cameraAr = intent?.getStringExtra(EXTRA_CAMERA_AR)?.ifBlank { ScrcpyDefaults.CAMERA_AR } ?: ScrcpyDefaults.CAMERA_AR
        val cameraFps = intent?.getStringExtra(EXTRA_CAMERA_FPS)?.ifBlank { ScrcpyDefaults.CAMERA_FPS } ?: ScrcpyDefaults.CAMERA_FPS
        val cameraHighSpeed = intent?.getBooleanExtra(EXTRA_CAMERA_HIGH_SPEED, ScrcpyDefaults.CAMERA_HIGH_SPEED) ?: ScrcpyDefaults.CAMERA_HIGH_SPEED
        val audioSourcePreset = intent?.getStringExtra(EXTRA_AUDIO_SOURCE_PRESET)?.ifBlank { ScrcpyDefaults.AUDIO_SOURCE_PRESET } ?: ScrcpyDefaults.AUDIO_SOURCE_PRESET
        val audioSourceCustom = intent?.getStringExtra(EXTRA_AUDIO_SOURCE_CUSTOM)?.ifBlank { ScrcpyDefaults.AUDIO_SOURCE_CUSTOM } ?: ScrcpyDefaults.AUDIO_SOURCE_CUSTOM
        val audioDup = intent?.getBooleanExtra(EXTRA_AUDIO_DUP, ScrcpyDefaults.AUDIO_DUP) ?: ScrcpyDefaults.AUDIO_DUP
        val noAudioPlayback = intent?.getBooleanExtra(EXTRA_NO_AUDIO_PLAYBACK, ScrcpyDefaults.NO_AUDIO_PLAYBACK) ?: ScrcpyDefaults.NO_AUDIO_PLAYBACK
        val requireAudio = intent?.getBooleanExtra(EXTRA_REQUIRE_AUDIO, ScrcpyDefaults.REQUIRE_AUDIO) ?: ScrcpyDefaults.REQUIRE_AUDIO
        val maxSizeInput = intent?.getStringExtra(EXTRA_MAX_SIZE_INPUT)?.ifBlank { ScrcpyDefaults.MAX_SIZE_INPUT } ?: ScrcpyDefaults.MAX_SIZE_INPUT
        val maxFpsInput = intent?.getStringExtra(EXTRA_MAX_FPS_INPUT)?.ifBlank { ScrcpyDefaults.MAX_FPS_INPUT } ?: ScrcpyDefaults.MAX_FPS_INPUT
        val videoEncoder = intent?.getStringExtra(EXTRA_VIDEO_ENCODER)?.ifBlank { ScrcpyDefaults.VIDEO_ENCODER } ?: ScrcpyDefaults.VIDEO_ENCODER
        val videoCodecOptions = intent?.getStringExtra(EXTRA_VIDEO_CODEC_OPTIONS)?.ifBlank { ScrcpyDefaults.VIDEO_CODEC_OPTION } ?: ScrcpyDefaults.VIDEO_CODEC_OPTION
        val audioEncoder = intent?.getStringExtra(EXTRA_AUDIO_ENCODER)?.ifBlank { ScrcpyDefaults.AUDIO_ENCODER } ?: ScrcpyDefaults.AUDIO_ENCODER
        val audioCodecOptions = intent?.getStringExtra(EXTRA_AUDIO_CODEC_OPTIONS)?.ifBlank { ScrcpyDefaults.AUDIO_CODEC_OPTION } ?: ScrcpyDefaults.AUDIO_CODEC_OPTION
        val newDisplayWidth = intent?.getStringExtra(EXTRA_NEW_DISPLAY_WIDTH)?.ifBlank { ScrcpyDefaults.NEW_DISPLAY_WIDTH } ?: ScrcpyDefaults.NEW_DISPLAY_WIDTH
        val newDisplayHeight = intent?.getStringExtra(EXTRA_NEW_DISPLAY_HEIGHT)?.ifBlank { ScrcpyDefaults.NEW_DISPLAY_HEIGHT } ?: ScrcpyDefaults.NEW_DISPLAY_HEIGHT
        val newDisplayDpi = intent?.getStringExtra(EXTRA_NEW_DISPLAY_DPI)?.ifBlank { ScrcpyDefaults.NEW_DISPLAY_DPI } ?: ScrcpyDefaults.NEW_DISPLAY_DPI
        val cropWidth = intent?.getStringExtra(EXTRA_CROP_WIDTH)?.ifBlank { ScrcpyDefaults.CROP_WIDTH } ?: ScrcpyDefaults.CROP_WIDTH
        val cropHeight = intent?.getStringExtra(EXTRA_CROP_HEIGHT)?.ifBlank { ScrcpyDefaults.CROP_HEIGHT } ?: ScrcpyDefaults.CROP_HEIGHT
        val cropX = intent?.getStringExtra(EXTRA_CROP_X)?.ifBlank { ScrcpyDefaults.CROP_X } ?: ScrcpyDefaults.CROP_X
        val cropY = intent?.getStringExtra(EXTRA_CROP_Y)?.ifBlank { ScrcpyDefaults.CROP_Y } ?: ScrcpyDefaults.CROP_Y

        val sessionParams = SessionParams(
            videoBitRateMbps = videoBitRateMbps,
            audioBitRateKbps = audioBitRateKbps,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            audioEnabled = audioEnabled,
            serverRemotePath = serverRemotePath,
            customServerUri = customServerUri,
            turnScreenOff = turnScreenOff,
            noControl = noControl,
            noVideo = noVideo,
            videoSourcePreset = videoSourcePreset,
            displayIdInput = displayIdInput,
            cameraIdInput = cameraIdInput,
            cameraFacingPreset = cameraFacingPreset,
            cameraSizePreset = cameraSizePreset,
            cameraSizeCustom = cameraSizeCustom,
            cameraAr = cameraAr,
            cameraFps = cameraFps,
            cameraHighSpeed = cameraHighSpeed,
            audioSourcePreset = audioSourcePreset,
            audioSourceCustom = audioSourceCustom,
            audioDup = audioDup,
            noAudioPlayback = noAudioPlayback,
            requireAudio = requireAudio,
            maxSizeInput = maxSizeInput,
            maxFpsInput = maxFpsInput,
            videoEncoder = videoEncoder,
            videoCodecOptions = videoCodecOptions,
            audioEncoder = audioEncoder,
            audioCodecOptions = audioCodecOptions,
            newDisplayWidth = newDisplayWidth,
            newDisplayHeight = newDisplayHeight,
            newDisplayDpi = newDisplayDpi,
            cropWidth = cropWidth,
            cropHeight = cropHeight,
            cropX = cropX,
            cropY = cropY,
        )

        enableEdgeToEdge()
        setContent {
            ShortcutLaunchScreen(
                deviceIp = deviceIp,
                devicePort = devicePort,
                deviceName = deviceName,
                sessionParams = sessionParams,
                onDismiss = { finish() }
            )
        }
    }

    internal data class SessionParams(
        val videoBitRateMbps: Float,
        val audioBitRateKbps: Int,
        val videoCodec: String,
        val audioCodec: String,
        val audioEnabled: Boolean,
        val serverRemotePath: String,
        val customServerUri: String?,
        val turnScreenOff: Boolean,
        val noControl: Boolean,
        val noVideo: Boolean,
        val videoSourcePreset: String,
        val displayIdInput: String,
        val cameraIdInput: String,
        val cameraFacingPreset: String,
        val cameraSizePreset: String,
        val cameraSizeCustom: String,
        val cameraAr: String,
        val cameraFps: String,
        val cameraHighSpeed: Boolean,
        val audioSourcePreset: String,
        val audioSourceCustom: String,
        val audioDup: Boolean,
        val noAudioPlayback: Boolean,
        val requireAudio: Boolean,
        val maxSizeInput: String,
        val maxFpsInput: String,
        val videoEncoder: String,
        val videoCodecOptions: String,
        val audioEncoder: String,
        val audioCodecOptions: String,
        val newDisplayWidth: String,
        val newDisplayHeight: String,
        val newDisplayDpi: String,
        val cropWidth: String,
        val cropHeight: String,
        val cropX: String,
        val cropY: String,
    )
}

@Composable
private fun ShortcutLaunchScreen(
    deviceIp: String,
    devicePort: Int,
    deviceName: String,
    sessionParams: ShortcutLaunchActivity.SessionParams,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val nativeCore = remember(context) { NativeCoreFacade.get(context.applicationContext) }
    val settings = remember(context) { loadMainSettings(context) }
    val scope = rememberCoroutineScope()

    var connectionState by remember { mutableStateOf<ConnectionState>(ConnectionState.Connecting) }
    var sessionInfo by remember { mutableStateOf<ScrcpySessionInfo?>(null) }
    var currentFps by remember { mutableFloatStateOf(0f) }
    var virtualButtonsLayout by remember { mutableStateOf(settings.virtualButtonsLayout) }
    var showDebugInfo by remember { mutableStateOf(settings.fullscreenDebugInfoEnabled) }
    var showVirtualButtons by remember { mutableStateOf(settings.showFullscreenVirtualButtons) }
    var cameraMirroringSupported by remember { mutableStateOf(true) }

    val virtualButtonLayout = remember(virtualButtonsLayout) {
        VirtualButtonActions.splitLayout(VirtualButtonActions.parseStoredLayout(virtualButtonsLayout))
    }
    val bar = remember(virtualButtonLayout) {
        VirtualButtonBar(
            outsideActions = virtualButtonLayout.first,
            moreActions = virtualButtonLayout.second,
        )
    }

    val themeController = remember { ThemeController(colorSchemeMode = ColorSchemeMode.Dark) }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    
    BackHandler(enabled = true) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000) {
            onDismiss()
        } else {
            lastBackPressTime = currentTime
            Toast.makeText(context, "再次返回以退出", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(nativeCore) {
        val listener: (Int, Int) -> Unit = { w, h ->
            sessionInfo?.let { sessionInfo = it.copy(width = w, height = h) }
        }
        nativeCore.addVideoSizeListener(listener)
        onDispose {
            nativeCore.removeVideoSizeListener(listener)
        }
    }

    DisposableEffect(nativeCore) {
        val listener: (Float) -> Unit = { fps -> currentFps = fps }
        nativeCore.addVideoFpsListener(listener)
        onDispose {
            nativeCore.removeVideoFpsListener(listener)
        }
    }

    LaunchedEffect(deviceIp, devicePort) {
        connectionState = ConnectionState.Connecting

        val connected = withContext(Dispatchers.IO) {
            nativeCore.adbConnect(deviceIp, devicePort)
        }

        if (!connected) {
            connectionState = ConnectionState.ConnectionFailed("ADB 连接失败")
            return@LaunchedEffect
        }

        val deviceInfo = withContext(Dispatchers.IO) {
            fetchConnectedDeviceInfo(nativeCore, deviceIp, devicePort)
        }
        cameraMirroringSupported = deviceInfo.sdkInt !in 0..<31

        connectionState = ConnectionState.StartingScrcpy

        val validationError = run {
            if (sessionParams.noVideo && !sessionParams.audioEnabled) {
                return@run "--no-video 需要同时启用音频"
            }
            if (sessionParams.audioEnabled && sessionParams.audioSourcePreset == "custom" && sessionParams.audioSourceCustom.isBlank()) {
                return@run "audio-source 选择自定义时不能为空"
            }
            val resolvedVideoSource = sessionParams.videoSourcePreset.trim().ifBlank { "display" }
            if (resolvedVideoSource == "camera" && !cameraMirroringSupported) {
                return@run "camera mirroring 需要 Android 12+ (SDK 31+)"
            }
            val resolvedCameraSize = when (sessionParams.cameraSizePreset) {
                "custom" -> sessionParams.cameraSizeCustom.trim()
                else -> sessionParams.cameraSizePreset.trim()
            }
            if (resolvedVideoSource == "camera" && sessionParams.cameraSizePreset == "custom" && resolvedCameraSize.isBlank()) {
                return@run "camera-size 选择自定义时不能为空"
            }
            val resolvedCameraId = sessionParams.cameraIdInput.trim()
            val resolvedCameraFacing = sessionParams.cameraFacingPreset.trim()
            if (resolvedVideoSource == "camera" && resolvedCameraId.isNotBlank() && resolvedCameraFacing.isNotBlank()) {
                return@run "camera-id 与 camera-facing 不能同时设置"
            }
            val resolvedCameraFps = sessionParams.cameraFps.filter(Char::isDigit).toIntOrNull() ?: 0
            if (resolvedVideoSource == "camera" && sessionParams.cameraHighSpeed && resolvedCameraFps <= 0) {
                return@run "启用 --camera-high-speed 时，--camera-fps 不能为 0"
            }
            val maxSize = sessionParams.maxSizeInput.filter(Char::isDigit).toIntOrNull()?.takeIf { it > 0 } ?: 0
            val resolvedCameraAr = sessionParams.cameraAr.trim()
            if (resolvedVideoSource == "camera" && resolvedCameraSize.isNotBlank() && (maxSize > 0 || resolvedCameraAr.isNotBlank())) {
                return@run "显式 camera-size 时不能同时设置 --max-size 或 --camera-ar"
            }
            null
        }

        if (validationError != null) {
            connectionState = ConnectionState.ConnectionFailed("参数错误: $validationError")
            return@LaunchedEffect
        }

        val maxSize = sessionParams.maxSizeInput.filter(Char::isDigit).toIntOrNull()?.takeIf { it > 0 } ?: 0
        val maxFps = sessionParams.maxFpsInput.filter(Char::isDigit).toIntOrNull()?.toFloat() ?: 0f
        val bitRateBps = (sessionParams.videoBitRateMbps * 1_000_000).toInt()
        val audioBitRateBps = (sessionParams.audioBitRateKbps.coerceAtLeast(1)) * 1_000

        val resolvedVideoSource = sessionParams.videoSourcePreset.trim().ifBlank { "display" }
        val resolvedCameraSize = when (sessionParams.cameraSizePreset) {
            "custom" -> sessionParams.cameraSizeCustom.trim()
            else -> sessionParams.cameraSizePreset.trim()
        }
        val resolvedCameraId = sessionParams.cameraIdInput.trim()
        val resolvedCameraFacing = sessionParams.cameraFacingPreset.trim()
        val resolvedCameraAr = sessionParams.cameraAr.trim()
        val resolvedCameraFps = sessionParams.cameraFps.filter(Char::isDigit).toIntOrNull() ?: 0

        val resolvedAudioSource = when (sessionParams.audioSourcePreset) {
            "custom" -> sessionParams.audioSourceCustom.trim()
            else -> sessionParams.audioSourcePreset.trim()
        }

        val newDisplayArg = buildNewDisplayArg(
            sessionParams.newDisplayWidth.filter(Char::isDigit),
            sessionParams.newDisplayHeight.filter(Char::isDigit),
            sessionParams.newDisplayDpi.filter(Char::isDigit),
        )
        val displayId = sessionParams.displayIdInput.filter(Char::isDigit).toIntOrNull()?.takeIf { it > 0 }
        val crop = buildCropArg(
            sessionParams.cropWidth.filter(Char::isDigit),
            sessionParams.cropHeight.filter(Char::isDigit),
            sessionParams.cropX.filter(Char::isDigit),
            sessionParams.cropY.filter(Char::isDigit),
        )
        val effectiveTurnScreenOff = sessionParams.turnScreenOff && !sessionParams.noControl

        val session = try {
            withContext(Dispatchers.IO) {
                nativeCore.scrcpyStart(
                    NativeCoreFacade.defaultStartRequest(
                        customServerUri = sessionParams.customServerUri,
                        maxSize = maxSize,
                        maxFps = maxFps,
                        videoBitRate = bitRateBps,
                        remotePath = sessionParams.serverRemotePath.trim(),
                        videoCodec = sessionParams.videoCodec,
                        audio = sessionParams.audioEnabled,
                        audioCodec = sessionParams.audioCodec,
                        audioBitRate = audioBitRateBps,
                        noControl = sessionParams.noControl,
                        videoEncoder = sessionParams.videoEncoder,
                        videoCodecOptions = sessionParams.videoCodecOptions,
                        audioEncoder = sessionParams.audioEncoder,
                        audioCodecOptions = sessionParams.audioCodecOptions,
                        audioDup = sessionParams.audioDup,
                        audioSource = resolvedAudioSource,
                        videoSource = resolvedVideoSource,
                        cameraId = resolvedCameraId,
                        cameraFacing = resolvedCameraFacing,
                        cameraSize = resolvedCameraSize,
                        cameraAr = resolvedCameraAr,
                        cameraFps = resolvedCameraFps,
                        cameraHighSpeed = sessionParams.cameraHighSpeed,
                        noAudioPlayback = sessionParams.noAudioPlayback,
                        noVideo = sessionParams.noVideo,
                        requireAudio = sessionParams.requireAudio,
                        turnScreenOff = effectiveTurnScreenOff,
                        newDisplay = newDisplayArg,
                        displayId = displayId,
                        crop = crop,
                    )
                )
            }
        } catch (e: Exception) {
            connectionState = ConnectionState.ConnectionFailed("scrcpy 启动失败: ${e.message}")
            return@LaunchedEffect
        }

        sessionInfo = session
        connectionState = ConnectionState.Connected(session)
    }

    DisposableEffect(Unit) {
        onDispose {
            scope.launch(Dispatchers.IO) {
                runCatching { nativeCore.scrcpyStop() }
                runCatching { nativeCore.adbDisconnect() }
            }
        }
    }

    MiuixTheme(controller = themeController) {
        Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                when (val state = connectionState) {
                    is ConnectionState.Connecting -> {
                        Box(modifier = Modifier.align(Alignment.Center)) {
                            Text("正在连接 $deviceName...")
                        }
                    }
                    is ConnectionState.StartingScrcpy -> {
                        Box(modifier = Modifier.align(Alignment.Center)) {
                            Text("正在启动 scrcpy...")
                        }
                    }
                    is ConnectionState.ConnectionFailed -> {
                        Box(modifier = Modifier.align(Alignment.Center)) {
                            Text(state.message)
                        }
                    }
                    is ConnectionState.Connected -> {
                        val session = state.session
                        FullscreenControlScreen(
                            session = session,
                            nativeCore = nativeCore,
                            onDismiss = onDismiss,
                            showDebugInfo = showDebugInfo,
                            currentFps = currentFps,
                            enableBackHandler = false,
                            onInjectTouch = { action, pointerId, x, y, pressure, buttons ->
                                nativeCore.scrcpyInjectTouch(
                                    action = action,
                                    pointerId = pointerId,
                                    x = x,
                                    y = y,
                                    screenWidth = session.width,
                                    screenHeight = session.height,
                                    pressure = pressure,
                                    actionButton = 0,
                                    buttons = buttons,
                                )
                            },
                        )

                        if (showVirtualButtons) {
                            bar.Fullscreen(
                                modifier = Modifier.align(Alignment.BottomCenter),
                                onAction = { action ->
                                    action.keycode?.let { keycode ->
                                        nativeCore.scrcpyInjectKeycode(0, keycode)
                                        nativeCore.scrcpyInjectKeycode(1, keycode)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildNewDisplayArg(width: String, height: String, dpi: String): String {
    val w = width.toIntOrNull()?.takeIf { it > 0 }
    val h = height.toIntOrNull()?.takeIf { it > 0 }
    val d = dpi.toIntOrNull()?.takeIf { it > 0 }
    val sizePart = if (w != null && h != null) "${w}x${h}" else ""
    return when {
        sizePart.isNotEmpty() && d != null -> "$sizePart/$d"
        sizePart.isNotEmpty() -> sizePart
        d != null -> "/$d"
        else -> ""
    }
}

private fun buildCropArg(width: String, height: String, x: String, y: String): String {
    val w = width.toIntOrNull()?.takeIf { it > 0 } ?: return ""
    val h = height.toIntOrNull()?.takeIf { it > 0 } ?: return ""
    val ox = x.toIntOrNull()?.takeIf { it >= 0 } ?: return ""
    val oy = y.toIntOrNull()?.takeIf { it >= 0 } ?: return ""
    return "$w:$h:$ox:$oy"
}

private sealed class ConnectionState {
    data object Connecting : ConnectionState()
    data object StartingScrcpy : ConnectionState()
    data class ConnectionFailed(val message: String) : ConnectionState()
    data class Connected(val session: ScrcpySessionInfo) : ConnectionState()
}
