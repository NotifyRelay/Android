package notifyrelay.domain.feature.media

import kotlinx.coroutines.flow.StateFlow
import notifyrelay.domain.feature.device.model.DeviceInfo

/** 远端媒体会话信息（平台无关视图）。 */
data class MediaSessionSnapshot(
    val packageName: String?,
    val title: String?,
    val artist: String?,
)

/**
 * 媒体控制端口。
 *
 * 播放控制、接收模式与当前会话均经本接口；实现位于平台侧。
 */
interface MediaControlPort {
    /** 当前会话。 */
    fun currentSession(): MediaSessionSnapshot?

    /** 当前会话所属设备。 */
    fun currentDevice(): DeviceInfo?

    /** 是否启用接收。 */
    fun isEnabled(): Boolean

    /** 当前接收模式。 */
    fun receiveMode(): MediaMessageReceiveMode

    /** 设置接收模式。 */
    fun setReceiveMode(mode: MediaMessageReceiveMode)

    /** 控制本机播放器。 */
    fun localControl(action: MediaControlAction)

    /** 向远端发送媒体指令；返回是否入队成功。 */
    fun sendToRemote(
        device: DeviceInfo,
        action: MediaControlAction,
    ): Boolean

    /** 会话变化信号。 */
    val sessionUpdates: StateFlow<MediaSessionSnapshot?>
}

/** 媒体控制动作。 */
enum class MediaControlAction { PLAY_PAUSE, PREVIOUS, NEXT }

/** 媒体接收模式。 */
enum class MediaReceiveMode { OFF, LOCAL, REMOTE }

/** [MediaControlPort] 的注册点。 */
object MediaControlPortRegistry {
    @Volatile
    var current: MediaControlPort? = null
        private set

    fun install(port: MediaControlPort) {
        current = port
    }
}
