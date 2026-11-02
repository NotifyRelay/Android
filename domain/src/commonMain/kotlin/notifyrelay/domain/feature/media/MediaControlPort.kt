package notifyrelay.domain.feature.media

import kotlinx.coroutines.flow.StateFlow
import notifyrelay.domain.feature.device.model.DeviceInfo

/** 媒体会话信息（平台无关视图）。 */
data class MediaSessionSnapshot(
    val packageName: String?,
    val title: String?,
    val artist: String?,
    /** 封面：data URL 或 http(s) 地址；无封面为 null。 */
    val coverUrl: String? = null,
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

    /**
     * 各设备会话的映射（键为设备 UUID，本机为 [LOCAL_SESSION_KEY]）。
     *
     * 平台未分设备维护时返回空映射，页面按「无会话」渲染。
     */
    fun sessionsFlow(): StateFlow<Map<String, MediaSessionSnapshot>> = emptySessionsFlow
}

/** 本机会话在分设备映射中的键。 */
const val LOCAL_SESSION_KEY: String = "local"

/** 空会话映射流（[MediaControlPort.sessionsFlow] 的默认实现用）。 */
private val emptySessionsFlow: StateFlow<Map<String, MediaSessionSnapshot>> = kotlinx.coroutines.flow.MutableStateFlow(emptyMap())

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
