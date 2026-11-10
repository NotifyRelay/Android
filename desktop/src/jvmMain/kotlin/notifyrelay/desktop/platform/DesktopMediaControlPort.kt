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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
 * - **状态上报**：本机会话按「换曲」与固定周期经 Rust 合并引擎推给在线设备；
 * - **接收模式**：与 Android 同键（`media_message_receive_mode`），落公共 `Storage`。
 *
 * 音频转发不在本端口内，见 [DesktopAudioRelayPort]。
 */
class DesktopMediaControlPort(
    private val onlineDevices: () -> List<DeviceInfo>,
    /** 向指定设备发送一条 `DATA_MEDIA_CONTROL` 报文；返回是否已交给发送链路。 */
    private val sendControl: (DeviceInfo, String) -> Boolean = { _, _ -> false },
    /** 向指定设备推送本机媒体全量状态；`isEnd` 为真表示会话已结束。返回是否已入队。 */
    private val sendMediaState: (DeviceInfo, String, Boolean) -> Boolean = { _, _, _ -> false },
) : MediaControlPort {
    private companion object {
        const val TAG = "DesktopMedia"
        const val KEY_RECEIVE_MODE = "media_message_receive_mode"

        /** 与 Android 同键：上报本机媒体状态的总开关。 */
        const val KEY_SEND_ENABLED = "send_media_notifications_enabled"

        /** 本机会话在分设备表里的键。 */
        const val LOCAL_KEY = "local"

        /** 会话轮询间隔：SMTC 桥只提供读取接口，无变更事件，故周期取快照。 */
        const val POLL_INTERVAL_MS = 1_000L

        /**
         * 状态上报的固定周期。
         *
         * 与 Rust 媒体心跳（6s）一致：进度本身由接收端按本地时钟推进，
         * 这里定期刷新只是重发计时基准，避免长曲目累积偏差。
         */
        const val PUSH_INTERVAL_MS = 6_000L
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

    /** 上一次上报的曲目签名（标题 + 艺术家 + 总时长）：变化即视为换曲。 */
    @Volatile
    private var lastPushedTrack: String? = null

    /** 上一次上报的时刻（毫秒）：固定周期的基准。 */
    @Volatile
    private var lastPushedAt: Long = 0L

    /** 是否已上报过本机会话：用于判定「从有到无」并发出结束标记。 */
    @Volatile
    private var localStatePublished: Boolean = false

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
        if (!acceptsRemoteUnder(mode)) {
            // 不再接受远端媒体时立即撤下远端会话，避免界面残留已弃用的状态；本机会话不受影响
            sessionsByDevice.update { current -> current.filterKeys { it == LOCAL_KEY } }
            currentDeviceState = null
            sessionState.value = sessionsByDevice.value[LOCAL_KEY]
        }
    }

    /**
     * 该接收模式下是否接受远端媒体。
     *
     * 与 Android 侧同口径：仅音频模式要求本机正在接收音频。
     */
    private fun acceptsRemoteUnder(mode: MediaMessageReceiveMode): Boolean =
        when (mode) {
            MediaMessageReceiveMode.On -> true
            MediaMessageReceiveMode.Off -> false
            MediaMessageReceiveMode.AudioOnly -> notifyrelay.desktop.platform.DesktopPcmPlayer.isRunning
        }

    /** 是否上报本机媒体状态；默认开启。 */
    fun isSendEnabled(): Boolean = Storage.getBoolean(KEY_SEND_ENABLED, true)

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
                    positionMs = it.positionMs,
                    durationMs = it.durationMs.takeIf { duration -> duration > 0L },
                    isPlaying = it.playing,
                    sampledAtMs = if (it.durationMs > 0L) System.currentTimeMillis() else 0L,
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
        publishLocalState(mapped)
    }

    /**
     * 上报本机会话。
     *
     * 触发条件：曲目签名变化（换曲）或距上次上报已满 [PUSH_INTERVAL_MS]；
     * 会话结束只上报一次结束标记。进度不单独触发上报。
     */
    private fun publishLocalState(mapped: MediaSessionSnapshot?) {
        if (!isSendEnabled()) {
            if (localStatePublished) {
                publishEnd()
            }
            return
        }
        if (mapped == null) {
            if (localStatePublished) publishEnd()
            return
        }
        val signature =
            listOf(
                mapped.title.orEmpty(),
                mapped.artist.orEmpty(),
                (mapped.durationMs ?: 0L).toString(),
            ).joinToString("")
        val now = System.currentTimeMillis()
        val changed = signature != lastPushedTrack
        val due = now - lastPushedAt >= PUSH_INTERVAL_MS
        if (!changed && !due) return

        val content = buildMediaFullContent(mapped)
        var enqueued = false
        onlineDevices().forEach { device ->
            val ok = sendMediaState(device, content, false)
            enqueued = enqueued || ok
            if (!ok) Logger.w(TAG, "本机媒体状态上报失败: 设备=" + device.displayName)
        }
        if (enqueued) {
            lastPushedTrack = signature
            lastPushedAt = now
            localStatePublished = true
        }
    }

    /** 上报会话结束标记；成功后清空上报状态，避免重复发送。 */
    private fun publishEnd() {
        var enqueued = false
        onlineDevices().forEach { device ->
            val ok = sendMediaState(device, "{}", true)
            enqueued = enqueued || ok
        }
        if (enqueued) {
            lastPushedTrack = null
            lastPushedAt = 0L
            localStatePublished = false
        }
    }

    /**
     * 组装媒体全量状态。
     *
     * 进度按超级岛计时器口径表达（`timerWhen`/`timerTotal`/`timerSystemCurrent`/`timerType`），
     * 与 Android 侧同一套字段：接收端据此在本地推进，发送端不按进度高频推送。
     */
    private fun buildMediaFullContent(snapshot: MediaSessionSnapshot): String {
        val pkg = snapshot.packageName.orEmpty()
        val now = System.currentTimeMillis()
        val position = snapshot.positionMs
        val total = snapshot.durationMs
        return buildJsonObject {
            put("packageName", JsonPrimitive(pkg))
            put("appName", JsonPrimitive(pkg))
            put("title", JsonPrimitive(snapshot.title.orEmpty()))
            put("text", JsonPrimitive(snapshot.artist.orEmpty()))
            put("coverUrl", JsonPrimitive(snapshot.coverUrl.orEmpty()))
            put("time", JsonPrimitive(now))
            put("isPlaying", JsonPrimitive(snapshot.isPlaying))
            if (position != null && position >= 0L && total != null && total > 0L) {
                put("timerType", JsonPrimitive(if (snapshot.isPlaying) 1 else 2))
                put("timerWhen", JsonPrimitive(now - position))
                put("timerTotal", JsonPrimitive(total))
                put("timerSystemCurrent", JsonPrimitive(now))
            }
        }.toString()
    }

    private fun SMTCLoaderAvailable(): Boolean = wtf.magma.audio.SMTCLoader.available
}
