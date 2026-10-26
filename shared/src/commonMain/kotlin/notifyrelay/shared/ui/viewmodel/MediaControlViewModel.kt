package notifyrelay.shared.ui.viewmodel

import androidx.lifecycle.ViewModel
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.media.MediaControlAction
import notifyrelay.domain.feature.media.MediaControlPort
import notifyrelay.domain.feature.media.MediaMessageReceiveMode
import notifyrelay.domain.feature.media.MediaSessionSnapshot

/**
 * 远端媒体控制的承载（平台无关）。
 */
class MediaControlViewModel(
    private val port: MediaControlPort,
) : ViewModel() {
    val sessionUpdates get() = port.sessionUpdates

    fun currentSession(): MediaSessionSnapshot? = port.currentSession()

    fun currentDevice(): DeviceInfo? = port.currentDevice()

    fun isEnabled(): Boolean = port.isEnabled()

    fun receiveMode(): MediaMessageReceiveMode = port.receiveMode()

    fun setReceiveMode(mode: MediaMessageReceiveMode) {
        port.setReceiveMode(mode)
    }

    fun previousLocal() {
        port.localControl(MediaControlAction.PREVIOUS)
    }

    fun playPauseLocal() {
        port.localControl(MediaControlAction.PLAY_PAUSE)
    }

    fun nextLocal() {
        port.localControl(MediaControlAction.NEXT)
    }

    /** 向远端发送媒体指令；返回是否成功。 */
    fun sendToRemote(
        device: DeviceInfo,
        action: String,
    ): Boolean =
        when (action) {
            "previous" -> port.sendToRemote(device, MediaControlAction.PREVIOUS)
            "playPause" -> port.sendToRemote(device, MediaControlAction.PLAY_PAUSE)
            "next" -> port.sendToRemote(device, MediaControlAction.NEXT)
            else -> false
        }

    /** 兼容旧调用：action 为 previous/playPause/next。 */
    fun sendMediaCommand(
        device: DeviceInfo,
        action: String,
    ): Boolean = sendToRemote(device, action)
}
