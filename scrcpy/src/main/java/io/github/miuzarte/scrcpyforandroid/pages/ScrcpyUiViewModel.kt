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
import io.github.miuzarte.scrcpyforandroid.services.saveMainSettings

class ScrcpyUiViewModel private constructor(private val app: Application) : ViewModel() {
    val nativeCore: NativeCoreFacade = NativeCoreFacade.get(app.applicationContext)

    private var mainSettings = loadMainSettings(app)
    private val deviceSettings = loadDevicePageSettings(app)

    private val _audioEnabled = mutableStateOf(mainSettings.audioEnabled)
    var audioEnabled: Boolean
        get() = _audioEnabled.value
        set(value) {
            _audioEnabled.value = value
            mainSettings = mainSettings.copy(audioEnabled = value)
            saveMainSettings(app, mainSettings)
        }

    private val _audioCodec = mutableStateOf(mainSettings.audioCodec)
    var audioCodec: String
        get() = _audioCodec.value
        set(value) {
            _audioCodec.value = value
            mainSettings = mainSettings.copy(audioCodec = value)
            saveMainSettings(app, mainSettings)
        }

    private val _videoCodec = mutableStateOf(mainSettings.videoCodec)
    var videoCodec: String
        get() = _videoCodec.value
        set(value) {
            _videoCodec.value = value
            mainSettings = mainSettings.copy(videoCodec = value)
            saveMainSettings(app, mainSettings)
        }

    private val _fullscreenDebugInfoEnabled = mutableStateOf(mainSettings.fullscreenDebugInfoEnabled)
    var fullscreenDebugInfoEnabled: Boolean
        get() = _fullscreenDebugInfoEnabled.value
        set(value) {
            _fullscreenDebugInfoEnabled.value = value
            mainSettings = mainSettings.copy(fullscreenDebugInfoEnabled = value)
            saveMainSettings(app, mainSettings)
        }

    private val _showFullscreenVirtualButtons = mutableStateOf(mainSettings.showFullscreenVirtualButtons)
    var showFullscreenVirtualButtons: Boolean
        get() = _showFullscreenVirtualButtons.value
        set(value) {
            _showFullscreenVirtualButtons.value = value
            mainSettings = mainSettings.copy(showFullscreenVirtualButtons = value)
            saveMainSettings(app, mainSettings)
        }

    private val _keepScreenOnWhenStreamingEnabled = mutableStateOf(mainSettings.keepScreenOnWhenStreamingEnabled)
    var keepScreenOnWhenStreamingEnabled: Boolean
        get() = _keepScreenOnWhenStreamingEnabled.value
        set(value) {
            _keepScreenOnWhenStreamingEnabled.value = value
            mainSettings = mainSettings.copy(keepScreenOnWhenStreamingEnabled = value)
            saveMainSettings(app, mainSettings)
        }

    private val _virtualButtonsLayout = mutableStateOf(mainSettings.virtualButtonsLayout)
    var virtualButtonsLayout: String
        get() = _virtualButtonsLayout.value
        set(value) {
            _virtualButtonsLayout.value = value
            mainSettings = mainSettings.copy(virtualButtonsLayout = value)
            saveMainSettings(app, mainSettings)
        }

    private val _customServerUri = mutableStateOf(mainSettings.customServerUri)
    var customServerUri: String?
        get() = _customServerUri.value
        set(value) {
            _customServerUri.value = value
            mainSettings = mainSettings.copy(customServerUri = value)
            saveMainSettings(app, mainSettings)
        }

    private val _serverRemotePath = mutableStateOf(mainSettings.serverRemotePath)
    var serverRemotePath: String
        get() = _serverRemotePath.value
        set(value) {
            _serverRemotePath.value = value
            mainSettings = mainSettings.copy(serverRemotePath = value)
            saveMainSettings(app, mainSettings)
        }

    val currentAdbKeyName: String
        get() = nativeCore.getAdbKeyName()

    private val _adbPairingAutoDiscoverOnDialogOpen = mutableStateOf(mainSettings.adbPairingAutoDiscoverOnDialogOpen)
    var adbPairingAutoDiscoverOnDialogOpen: Boolean
        get() = _adbPairingAutoDiscoverOnDialogOpen.value
        set(value) {
            _adbPairingAutoDiscoverOnDialogOpen.value = value
            mainSettings = mainSettings.copy(adbPairingAutoDiscoverOnDialogOpen = value)
            saveMainSettings(app, mainSettings)
        }

    private val _adbAutoReconnectPairedDevice = mutableStateOf(mainSettings.adbAutoReconnectPairedDevice)
    var adbAutoReconnectPairedDevice: Boolean
        get() = _adbAutoReconnectPairedDevice.value
        set(value) {
            _adbAutoReconnectPairedDevice.value = value
            mainSettings = mainSettings.copy(adbAutoReconnectPairedDevice = value)
            saveMainSettings(app, mainSettings)
        }

    private val _adbMdnsLanDiscoveryEnabled = mutableStateOf(mainSettings.adbMdnsLanDiscoveryEnabled)
    var adbMdnsLanDiscoveryEnabled: Boolean
        get() = _adbMdnsLanDiscoveryEnabled.value
        set(value) {
            _adbMdnsLanDiscoveryEnabled.value = value
            mainSettings = mainSettings.copy(adbMdnsLanDiscoveryEnabled = value)
            saveMainSettings(app, mainSettings)
        }

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

    companion object {
        @Volatile
        private var instance: ScrcpyUiViewModel? = null

        fun getInstance(app: Application): ScrcpyUiViewModel {
            return instance ?: synchronized(this) {
                instance ?: ScrcpyUiViewModel(app).also { instance = it }
            }
        }
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ScrcpyUiViewModel::class.java)) {
                return getInstance(app) as T
            }
            error("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
