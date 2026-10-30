package notifyrelay.desktop.platform

import dev.nucleusframework.media.control.MediaControlEvent
import dev.nucleusframework.media.control.MediaControlService
import dev.nucleusframework.media.control.MediaMetadata
import dev.nucleusframework.media.control.MediaPlaybackState
import dev.nucleusframework.media.control.MediaPlaybackStatus
import notifyrelay.base.log.Logger
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.media.MediaSessionSnapshot
import javax.swing.SwingUtilities

/**
 * 系统媒体中心接线。
 *
 * 音频中继播放期间，以本应用身份向系统媒体中心发布正在播放的信息；
 * 系统媒体按键转发为对源设备的媒体控制报文。
 *
 * Windows SMTC 走 WinRT/COM，要求调用线程处于单线程套间，
 * 因此所有媒体中心调用统一切到 Swing 事件线程执行。
 */
internal object DesktopSystemMediaControl {
    private const val TAG = "DesktopSystemMedia"

    private var attached = false
    private var relayDevice: DeviceInfo? = null
    private var relayDeviceName: String? = null
    private var relaySnapshot: MediaSessionSnapshot? = null
    private var sendControl: ((DeviceInfo, String) -> Boolean)? = null
    private var sessionProvider: ((String) -> MediaSessionSnapshot?)? = null

    /** 系统媒体中心后端是否可用。 */
    fun isAvailable(): Boolean = runCatching { MediaControlService.isAvailable() }.getOrDefault(false)

    /**
     * 登记控制报文发送通道；由数据层在装配时调用。
     *
     * @param sendControl 向指定设备发送一条 `DATA_MEDIA_CONTROL` 报文
     */
    fun setSendControl(sendControl: (DeviceInfo, String) -> Boolean) {
        this.sendControl = sendControl
    }

    /**
     * 登记媒体会话查询入口；由数据层在装配时调用。
     *
     * 中继开始时按设备 uuid 取当前会话，避免「会话先存在、中继后开始」时退回兜底曲目。
     */
    fun setSessionProvider(provider: (String) -> MediaSessionSnapshot?) {
        sessionProvider = provider
    }

    /**
     * 中继接收开始：确保接线并发布为正在播放。
     *
     * @param device 音频来源设备；未能定位时为空，此时仅按 [fallbackName] 发布曲目信息
     * @param fallbackName 设备未能定位时展示的设备名
     */
    fun onRelayStarted(
        device: DeviceInfo?,
        fallbackName: String = "对端设备",
    ) {
        relayDevice = device
        relayDeviceName = device?.displayName ?: fallbackName
        // 会话可能早于中继存在：开始时按设备 uuid 取一次当前会话
        relaySnapshot = device?.uuid?.let { sessionProvider?.invoke(it) }
        Logger.i(
            TAG,
            "中继开始: " + relayDeviceName +
                " 会话=" + (relaySnapshot?.title ?: "无"),
        )
        publish()
    }

    /** 中继接收结束：发布停止态、清空元数据并解除接线。 */
    fun onRelayStopped() {
        relayDevice = null
        relayDeviceName = null
        relaySnapshot = null
        if (!attached) return
        publish()
        onEdt {
            runCatching { MediaControlService.setMetadata(MediaMetadata()) }
            runCatching { MediaControlService.detach() }
        }
        attached = false
        Logger.i(TAG, "系统媒体中心已解除接线")
    }

    /** 源设备的媒体会话更新：同步标题、艺人与封面。 */
    fun onSessions(sessions: Map<String, MediaSessionSnapshot>) {
        val device = relayDevice ?: return
        val next = sessions[device.uuid]
        if (next == relaySnapshot) return
        relaySnapshot = next
        publish()
    }

    /** 退出时解除接线。 */
    fun stop() {
        relayDevice = null
        relayDeviceName = null
        relaySnapshot = null
        if (!attached) return
        attached = false
        onEdt { runCatching { MediaControlService.detach() } }
    }

    /** 首次发布前接线：配置身份并注册系统媒体按键回调。 */
    private fun attachIfNeeded() {
        if (attached) return
        if (!isAvailable()) {
            Logger.w(TAG, "系统媒体中心不可用，跳过接线")
            return
        }
        Logger.i(TAG, "系统媒体中心可用=" + isAvailable())
        var ok = false
        onEdt {
            runCatching {
                MediaControlService.configure(
                    dbusName = "org.mpris.MediaPlayer2.notifyrelay",
                    displayName = "NotifyRelay",
                )
            }.onFailure { Logger.w(TAG, "系统媒体中心配置失败", it) }
            Logger.i(TAG, "应用标识=" + dev.nucleusframework.core.runtime.NucleusApp.aumid)
            ok =
                runCatching {
                    MediaControlService.attach { event -> handleEvent(event) }
                }.onFailure { Logger.w(TAG, "系统媒体中心监听注册失败", it) }.isSuccess
        }
        attached = ok
        Logger.i(TAG, "系统媒体中心接线结果=" + ok)
    }

    /** 发布当前中继状态与元数据。 */
    private fun publish() {
        // 兜底：接线未完成时在此补齐（启动接线可能早于原生库加载）
        attachIfNeeded()
        if (!attached) return
        val name = relayDeviceName
        if (name == null) {
            onEdt {
                runCatching {
                    MediaControlService.setPlaybackState(MediaPlaybackState(MediaPlaybackStatus.STOPPED))
                }
            }
            return
        }
        // 每次发布都取最新会话：会话晚到时无需等待 push
        relayDevice?.uuid?.let { uuid -> sessionProvider?.invoke(uuid)?.let { relaySnapshot = it } }
        val snapshot = relaySnapshot
        // 有源设备会话时用其曲目信息；否则标题为「音频中继」、艺人为「来自：{设备名}」
        val fallbackTitle = "音频中继"
        val fallbackArtist = "来自：" + name
        Logger.i(
            TAG,
            "发布: 标题=" + (snapshot?.title ?: fallbackTitle) +
                " 艺人=" + (snapshot?.artist ?: fallbackArtist) +
                " 设备=" + name,
        )
        onEdt {
            runCatching {
                MediaControlService.setMetadata(
                    MediaMetadata(
                        title = snapshot?.title ?: fallbackTitle,
                        artist = snapshot?.artist ?: fallbackArtist,
                        // 有会话封面用会话封面，无封面时用应用图标
                        coverUrl = DesktopMediaCover.resolve(snapshot?.coverUrl),
                    ),
                )
                MediaControlService.setPlaybackState(MediaPlaybackState(MediaPlaybackStatus.PLAYING))
            }.onFailure { Logger.w(TAG, "系统媒体中心发布失败", it) }
        }
    }

    /**
     * 系统媒体按键：转发为对源设备的控制报文。
     *
     * 动作名与 Android 侧 `handleMediaControl` 一致（playPause / next / previous），
     * 使对端以本机媒体键执行播放控制。
     */
    private fun handleEvent(event: MediaControlEvent) {
        val device = relayDevice ?: return
        val action =
            when (event) {
                MediaControlEvent.Play, MediaControlEvent.Pause, MediaControlEvent.Toggle -> "playPause"
                MediaControlEvent.Next -> "next"
                MediaControlEvent.Previous -> "previous"
                // 停止在语义上等同暂停，对端无独立停止动作
                MediaControlEvent.Stop -> "playPause"
                else -> null
            } ?: return
        val sent = sendControl?.invoke(device, """{"type":"MEDIA_CONTROL","action":"$action"}""") ?: false
        Logger.i(TAG, "系统媒体按键: " + action + " 设备=" + relayDeviceName + " 已发送=" + sent)
    }

    /** 在 Swing 事件线程上执行；调用线程不是 EDT 时同步等待。 */
    private fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            runCatching { SwingUtilities.invokeAndWait(block) }
        }
    }
}
