package notifyrelay.desktop.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.data.config.SuperIslandConfigPolicy
import notifyrelay.domain.feature.media.AudioOperationResult
import notifyrelay.domain.feature.media.AudioRelayPort
import notifyrelay.domain.feature.media.MediaControlAction
import notifyrelay.domain.feature.media.MediaControlPort
import notifyrelay.domain.feature.media.MediaMessageReceiveMode
import notifyrelay.domain.feature.media.MediaSessionSnapshot
import notifyrelay.domain.feature.notification.superisland.SpecInjectionMode
import notifyrelay.domain.feature.notification.superisland.SuperIslandConfigPort
import notifyrelay.domain.platform.FormFactorPortRegistry

// 桌面端当前不承载音频中继、媒体控制与超级岛展示；
// 超级岛配置与历史读写已切换到公共实现，其余给出中性实现以保证界面可渲染。

/** 音频中继：桌面端未接入音频通道，一律返回失败原因。 */
class DesktopAudioRelayPort : AudioRelayPort {
    override fun relayMode(): Int = 0

    override fun requestSendAudio(device: notifyrelay.domain.feature.device.model.DeviceInfo): AudioOperationResult = AudioOperationResult.Failure("桌面端暂不支持音频转发")

    override fun requestReceiveAudio(device: notifyrelay.domain.feature.device.model.DeviceInfo): AudioOperationResult = AudioOperationResult.Failure("桌面端暂不支持音频转发")

    override fun stopAudio() = Unit
}

/** 媒体控制：桌面端未接入媒体会话，会话为空、控制为空操作。 */
class DesktopMediaControlPort : MediaControlPort {
    private val noSession = MutableStateFlow<MediaSessionSnapshot?>(null)
    override val sessionUpdates: StateFlow<MediaSessionSnapshot?> = noSession

    override fun currentSession(): MediaSessionSnapshot? = null

    override fun currentDevice(): notifyrelay.domain.feature.device.model.DeviceInfo? = null

    override fun isEnabled(): Boolean = false

    override fun receiveMode(): MediaMessageReceiveMode = MediaMessageReceiveMode.Off

    override fun setReceiveMode(mode: MediaMessageReceiveMode) = Unit

    override fun localControl(action: MediaControlAction) = Unit

    override fun sendToRemote(
        device: notifyrelay.domain.feature.device.model.DeviceInfo,
        action: MediaControlAction,
    ): Boolean = false
}

/**
 * 超级岛配置：键与互斥推导委托公共策略。
 *
 * 桌面端非平板、无系统版本判定，故浮窗默认开、列表模式默认关；
 * 展示层未接线，[dismissAllRemote] 与 [switchChannel] 保持空操作。
 */
class DesktopSuperIslandConfigPort : SuperIslandConfigPort {
    private val formFactor get() = FormFactorPortRegistry.current

    override fun isRemoteDisplayEnabled(): Boolean = SuperIslandConfigPolicy.isRemoteDisplayEnabled()

    override fun setRemoteDisplayEnabled(enabled: Boolean) = SuperIslandConfigPolicy.setRemoteDisplayEnabled(enabled)

    override fun isFloatingWindowEnabled(): Boolean = SuperIslandConfigPolicy.isFloatingWindowEnabled(formFactor)

    override fun setFloatingWindowEnabled(enabled: Boolean): Boolean = SuperIslandConfigPolicy.setFloatingWindowEnabled(formFactor, enabled)

    override fun isNotificationListMode(): Boolean = SuperIslandConfigPolicy.isNotificationListMode(formFactor)

    override fun setNotificationListMode(enabled: Boolean): Boolean = SuperIslandConfigPolicy.setNotificationListMode(formFactor, enabled)

    override fun specInjectionMode(): SpecInjectionMode = SuperIslandConfigPolicy.specInjectionMode()

    override fun setSpecInjectionMode(mode: SpecInjectionMode) = SuperIslandConfigPolicy.setSpecInjectionMode(mode)

    override fun dismissAllRemote() = Unit

    override fun switchChannel() = Unit
}
