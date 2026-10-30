package notifyrelay.desktop.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    /** 向指定设备发送一条 `DATA_MEDIA_CONTROL` 报文；返回是否已交给发送链路。 */
    private val sendControl: (DeviceInfo, String) -> Boolean = { _, _ -> false },
) : MediaControlPort {
    private companion object {
        const val TAG = "DesktopMedia"
        const val KEY_RECEIVE_MODE = "media_message_receive_mode"

        /** 本机会话在分设备表里的键。 */
        const val LOCAL_KEY = "local"

        /** 会话轮询间隔：SMTC 桥只提供读取接口，无变更事件，故周期取快照。 */
        const val POLL_INTERVAL_MS = 1_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 各设备各自的会话。
     *
     * 键为设备 UUID；本机会话固定用 [LOCAL_KEY]。分设备保存，
     * 避免多台设备（含本机）的媒体状态互相覆盖。
     */
    private val sessionsByDevice = MutableStateFlow<Map<String, MediaSessionSnapshot>>(emptyMap())

    /** 对外仍暴露「当前会话」：优先本机，其次最近更新的远端。 */
    private val sessionState = MutableStateFlow<MediaSessionSnapshot?>(null)
    override val sessionUpdates: StateFlow<MediaSessionSnapshot?> = sessionState.asStateFlow()

    private var pollJob: Job? = null

    /** 当前会话所属设备（本机会话为 null）。 */
    @Volatile
    private var currentDeviceState: DeviceInfo? = null

    override fun currentSession(): MediaSessionSnapshot? = sessionState.value

    override fun currentDevice(): DeviceInfo? = currentDeviceState

    /** 取指定设备的会话；[deviceUuid] 为空表示本机。 */
    fun sessionOf(deviceUuid: String?): MediaSessionSnapshot? = sessionsByDevice.value[deviceUuid ?: LOCAL_KEY]

    /** 指定设备的会话变化流。 */
    override fun sessionsFlow(): StateFlow<Map<String, MediaSessionSnapshot>> = sessionsByDevice.asStateFlow()

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
     * 动作名与 Android 侧 `handleMediaControl` 一致，由对端以本机媒体键执行；
     * 本机不再代为执行媒体键。
     */
    override fun sendToRemote(
        device: DeviceInfo,
        action: MediaControlAction,
    ): Boolean {
        if (onlineDevices().none { it.uuid == device.uuid }) return false
        val name =
            when (action) {
                MediaControlAction.PLAY_PAUSE -> "playPause"
                MediaControlAction.PREVIOUS -> "previous"
                MediaControlAction.NEXT -> "next"
            }
        val sent = sendControl(device, """{"type":"MEDIA_CONTROL","action":"$name"}""")
        if (!sent) Logger.w(TAG, "媒体指令下发失败: " + name + " 设备=" + device.displayName)
        return sent
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

    /** 更新远端回推的会话（来自 `onMediaPlay` 报文）；按设备各自保存。 */
    fun updateRemoteSession(
        device: DeviceInfo?,
        snapshot: MediaSessionSnapshot?,
    ) {
        val key = device?.uuid ?: LOCAL_KEY
        sessionsByDevice.update { current ->
            if (snapshot == null) current - key else current + (key to snapshot)
        }
        // 对外「当前会话」：优先反映最近一次远端回推（页面按设备取用时走 sessionOf）
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
                    coverUrl = it.coverDataUrl,
                )
            }
        // 本机会话单独占一个键，不与远端设备的会话互相覆盖
        sessionsByDevice.update { current ->
            if (mapped == null) current - LOCAL_KEY else current + (LOCAL_KEY to mapped)
        }
        // 对外「当前会话」以本机为准（本机无会话时保留最近一次远端回推）
        if (mapped != null) {
            currentDeviceState = null
            sessionState.value = mapped
        } else if (currentDeviceState == null) {
            sessionState.value = null
        }
    }

    private fun SMTCLoaderAvailable(): Boolean = wtf.magma.audio.SMTCLoader.available
}
