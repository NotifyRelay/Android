package notifyrelay.domain.feature.media

import notifyrelay.domain.feature.device.model.DeviceInfo

/**
 * 音频转发端口。
 *
 * 发送/接收/停止音频转发均经本接口；实现位于平台侧。
 */
interface AudioRelayPort {
    /** 当前转发方式（0 = scrcpy，1 = 中继）。 */
    fun relayMode(): Int

    /** 请求本端音频发送到 [device]；返回操作结果。 */
    fun requestSendAudio(device: DeviceInfo): AudioOperationResult

    /** 播放 [device] 的音频；返回操作结果。 */
    fun requestReceiveAudio(device: DeviceInfo): AudioOperationResult

    /** 停止音频转发。 */
    fun stopAudio()
}

/** 音频操作结果。 */
sealed interface AudioOperationResult {
    /** 成功，附带给用户的消息。 */
    data class Success(
        val message: String,
    ) : AudioOperationResult

    /** 失败，附带给用户的消息。 */
    data class Failure(
        val message: String,
    ) : AudioOperationResult
}

/** [AudioRelayPort] 的注册点。 */
object AudioRelayPortRegistry {
    @Volatile
    var current: AudioRelayPort? = null
        private set

    fun install(port: AudioRelayPort) {
        current = port
    }
}
