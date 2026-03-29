package io.github.miuzarte.scrcpyforandroid.pages

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.services.loadDevicePageSettings
import io.github.miuzarte.scrcpyforandroid.services.loadMainSettings

class ScrcpyUiViewModel(private val app: Application) : ViewModel() {
    val nativeCore: NativeCoreFacade = NativeCoreFacade.get(app.applicationContext)

    private val mainSettings = loadMainSettings(app)
    private val deviceSettings = loadDevicePageSettings(app)

    var audioEnabled by mutableStateOf(mainSettings.audioEnabled)
    var audioCodec by mutableStateOf(mainSettings.audioCodec)
    var videoCodec by mutableStateOf(mainSettings.videoCodec)
    var fullscreenDebugInfoEnabled by mutableStateOf(mainSettings.fullscreenDebugInfoEnabled)
    var showFullscreenVirtualButtons by mutableStateOf(mainSettings.showFullscreenVirtualButtons)
    var showPreviewVirtualButtonText by mutableStateOf(mainSettings.showPreviewVirtualButtonText)
    var keepScreenOnWhenStreamingEnabled by mutableStateOf(mainSettings.keepScreenOnWhenStreamingEnabled)
    var devicePreviewCardHeightDp by mutableStateOf(mainSettings.devicePreviewCardHeightDp)
    var virtualButtonsLayout by mutableStateOf(mainSettings.virtualButtonsLayout)
    var customServerUri by mutableStateOf(mainSettings.customServerUri)
    var serverRemotePath by mutableStateOf(mainSettings.serverRemotePath)
    var adbKeyName by mutableStateOf(mainSettings.adbKeyName)
    var adbPairingAutoDiscoverOnDialogOpen by mutableStateOf(
        mainSettings.adbPairingAutoDiscoverOnDialogOpen,
    )
    var adbAutoReconnectPairedDevice by mutableStateOf(mainSettings.adbAutoReconnectPairedDevice)
    var adbMdnsLanDiscoveryEnabled by mutableStateOf(mainSettings.adbMdnsLanDiscoveryEnabled)

    private var noControlState by mutableStateOf(deviceSettings.noControl)
    var noControl: Boolean
        get() = noControlState
        set(value) {
            noControlState = value
            if (value) {
                turnScreenOff = false
            }
        }

    var videoEncoder by mutableStateOf(deviceSettings.videoEncoder)
    var videoCodecOptions by mutableStateOf(deviceSettings.videoCodecOptions)
    var audioEncoder by mutableStateOf(deviceSettings.audioEncoder)
    var audioCodecOptions by mutableStateOf(deviceSettings.audioCodecOptions)
    var audioDup by mutableStateOf(deviceSettings.audioDup)
    var audioSourcePreset by mutableStateOf(deviceSettings.audioSourcePreset)
    var audioSourceCustom by mutableStateOf(deviceSettings.audioSourceCustom)
    var videoSourcePreset by mutableStateOf(deviceSettings.videoSourcePreset)
    var cameraIdInput by mutableStateOf(deviceSettings.cameraIdInput)
    var cameraFacingPreset by mutableStateOf(deviceSettings.cameraFacingPreset)
    var cameraSizePreset by mutableStateOf(deviceSettings.cameraSizePreset)
    var cameraSizeCustom by mutableStateOf(deviceSettings.cameraSizeCustom)
    var cameraArInput by mutableStateOf(deviceSettings.cameraAr)
    var cameraFpsInput by mutableStateOf(deviceSettings.cameraFps)
    var cameraHighSpeed by mutableStateOf(deviceSettings.cameraHighSpeed)
    var noAudioPlayback by mutableStateOf(deviceSettings.noAudioPlayback)
    var noVideo by mutableStateOf(deviceSettings.noVideo)
    var requireAudio by mutableStateOf(deviceSettings.requireAudio)
    var turnScreenOff by mutableStateOf(deviceSettings.turnScreenOff)
    var maxSizeInput by mutableStateOf(deviceSettings.maxSizeInput)
    var maxFpsInput by mutableStateOf(deviceSettings.maxFpsInput)
    var newDisplayWidth by mutableStateOf(deviceSettings.newDisplayWidth)
    var newDisplayHeight by mutableStateOf(deviceSettings.newDisplayHeight)
    var newDisplayDpi by mutableStateOf(deviceSettings.newDisplayDpi)
    var displayIdInput by mutableStateOf(deviceSettings.displayIdInput)
    var cropWidth by mutableStateOf(deviceSettings.cropWidth)
    var cropHeight by mutableStateOf(deviceSettings.cropHeight)
    var cropX by mutableStateOf(deviceSettings.cropX)
    var cropY by mutableStateOf(deviceSettings.cropY)

    val videoEncoderOptions = mutableStateListOf<String>()
    val audioEncoderOptions = mutableStateListOf<String>()
    val videoEncoderTypeMap = mutableStateMapOf<String, String>()
    val audioEncoderTypeMap = mutableStateMapOf<String, String>()
    val cameraSizeOptions = mutableStateListOf<String>()

    var sessionStarted by mutableStateOf(false)
    var canClearLogs by mutableStateOf(false)
    var refreshEncodersAction by mutableStateOf<(() -> Unit)?>(null)
    var refreshCameraSizesAction by mutableStateOf<(() -> Unit)?>(null)
    var clearLogsAction by mutableStateOf<(() -> Unit)?>(null)
    var openReorderDevicesAction by mutableStateOf<(() -> Unit)?>(null)
    var fullscreenLaunch by mutableStateOf<FullscreenControlLaunch?>(null)

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ScrcpyUiViewModel::class.java)) {
                return ScrcpyUiViewModel(app) as T
            }
            error("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
