package notifyrelay.shared.ui.viewmodel

import androidx.lifecycle.ViewModel
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.media.AudioOperationResult
import notifyrelay.domain.feature.media.AudioRelayPort

/**
 * 音频转发操作的承载（平台无关）。
 */
class AudioRelayViewModel(
    private val port: AudioRelayPort,
) : ViewModel() {
    /** 发送本端音频到对端；返回提示消息。 */
    fun requestSendAudio(device: DeviceInfo): String = port.requestSendAudio(device).message()

    /** 播放对端音频；返回提示消息。 */
    fun requestReceiveAudio(device: DeviceInfo): String = port.requestReceiveAudio(device).message()

    /** 停止音频转发。 */
    fun stopAudio() {
        port.stopAudio()
    }

    /** 当前转发方式（0 = scrcpy，1 = 中继）。 */
    fun relayMode(): Int = port.relayMode()

    private fun AudioOperationResult.message(): String =
        when (this) {
            is AudioOperationResult.Success -> message
            is AudioOperationResult.Failure -> message
        }
}
