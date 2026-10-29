package notifyrelay.desktop.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import notifyrelay.base.log.Logger
import notifyrelay.data.Storage
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.media.MediaControlAction
import notifyrelay.domain.feature.media.MediaControlPort
import notifyrelay.domain.feature.media.MediaMessageReceiveMode
import notifyrelay.domain.feature.media.MediaSessionSnapshot

/**
 * 桌面端媒体控制端口。
 *
 * 与 Windows 端分工一致：
 * - **本机会话读取**：经 SMTC Rust 桥（`desktop/smtc-rust`）取标题、艺术家、进度与播放态，周期刷新；
 * - **控制下发**：远端的播放/暂停/切歌指令以系统媒体键注入（[DesktopMediaKeySender]），
 *   由当前持有会话的应用响应；
 * - **接收模式**：与 Android 同键（`media_message_receive_mode`），落公共 `Storage`。
 *
 * 音频转发不在本端口内，见 [DesktopAudioRelayPort]。
 */
class DesktopMediaControlPort(
    private val onlineDevices: () -> List<DeviceInfo>,
) : MediaControlPort {
    private companion object {
        const val TAG = "DesktopMedia"
        const val KEY_RECEIVE_MODE = "media_message_receive_mode"

        /** 会话轮询间隔：SMTC 桥只提供读取接口，无变更事件，故周期取快照。 */
        const val POLL_INTERVAL_MS = 1_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sessionState = MutableStateFlow<MediaSessionSnapshot?>(null)
    override val sessionUpdates: StateFlow<MediaSessionSnapshot?> = sessionState.asStateFlow()

    private var pollJob: Job? = null

    /** 最近一次读取到的会话所属设备（远端回推时为对应设备，本机会话为 null）。 */
    private var currentDeviceState: DeviceInfo? = null

    override fun currentSession(): MediaSessionSnapshot? = sessionState.value

    override fun currentDevice(): DeviceInfo? = currentDeviceState

    override fun isEnabled(): Boolean = receiveMode() != MediaMessageReceiveMode.Off

    override fun receiveMode(): MediaMessageReceiveMode {
        val stored = Storage.getString(KEY_RECEIVE_MODE, MediaMessageReceiveMode.On.name)
        return runCatching { MediaMessageReceiveMode.valueOf(stored) }.getOrDefault(MediaMessageReceiveMode.On)
    }

    override fun setReceiveMode(mode: MediaMessageReceiveMode) {
        Storage.putString(KEY_RECEIVE_MODE, mode.name)
    }

    /** 控制本机播放器：以系统媒体键下发。 */
    override fun localControl(action: MediaControlAction) {
        val sent =
            when (action) {
                MediaControlAction.PLAY_PAUSE -> DesktopMediaKeySender.playPause()
                MediaControlAction.PREVIOUS -> DesktopMediaKeySender.previous()
                MediaControlAction.NEXT -> DesktopMediaKeySender.next()
            }
        if (!sent) Logger.w(TAG, "媒体键下发失败: " + action)
    }

    /**
     * 向远端设备发送控制指令。
     *
     * 桌面端只负责读取本机会话并展示，远端播放器由对端自行控制；
     * 故此处仅在本机存在对应会话时下发媒体键，返回是否成功。
     */
    override fun sendToRemote(
        device: DeviceInfo,
        action: MediaControlAction,
    ): Boolean {
        if (onlineDevices().none { it.uuid == device.uuid }) return false
        localControl(action)
        return true
    }

    /** 启动本机会话轮询。 */
    fun start() {
        if (pollJob != null) return
        if (!SMTCLoaderAvailable()) {
            Logger.w(TAG, "SMTC 桥不可用，媒体会话不刷新")
            return
        }
        pollJob =
            scope.launch {
                while (true) {
                    refresh()
                    delay(POLL_INTERVAL_MS)
                }
            }
    }

    /** 停止轮询。 */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    /** 更新远端回推的会话（来自 `onMediaPlay` 报文）。 */
    fun updateRemoteSession(
        device: DeviceInfo?,
        snapshot: MediaSessionSnapshot?,
    ) {
        currentDeviceState = device
        sessionState.value = snapshot
    }

    /** 读取一次本机会话并更新状态。 */
    private fun refresh() {
        val snapshot = DesktopMediaSessionSource.read()
        val mapped =
            snapshot?.let {
                MediaSessionSnapshot(
                    packageName = it.sourceAppId,
                    title = it.title,
                    artist = it.artist,
                )
            }
        // 本机会话优先；无本机会话时保留远端回推的内容
        if (mapped != null) {
            currentDeviceState = null
            sessionState.value = mapped
        } else if (currentDeviceState == null) {
            sessionState.value = null
        }
    }

    private fun SMTCLoaderAvailable(): Boolean = wtf.magma.audio.SMTCLoader.available
}
