package notifyrelay.desktop.device

import com.xzyht.notifyrelay.nativecore.device.service.callback.DataMessagePort
import notifyrelay.domain.feature.device.model.DeviceInfo

/**
 * 桌面端入站数据报文端口。
 *
 * 只承载通知报文的入站广播：聊天测试等订阅方经 [DesktopDeviceManager] 的回调集合接收。
 * 媒体、图标、应用列表、剪贴板、状态与超级岛报文在桌面端尚无业务处理，按空实现忽略，
 * 接入时在此按报文类型补分支。
 */
internal class DesktopDataMessagePort(
    private val manager: DesktopDeviceManager,
) : DataMessagePort {
    override fun onNotification(
        deviceUuid: String,
        payload: String,
    ) = manager.dispatchNotification(payload)

    override fun onMediaPlay(
        device: DeviceInfo,
        payload: String,
    ) = Unit

    override fun onIconRequest(
        device: DeviceInfo,
        payload: String,
    ) = Unit

    override fun onIconResponse(payload: String) = Unit

    override fun onAppListRequest(
        device: DeviceInfo,
        payload: String,
    ) = Unit

    override fun onAppListResponse(
        deviceUuid: String,
        payload: String,
    ) = Unit

    override fun onMediaControl(
        deviceUuid: String,
        payload: String,
    ) = Unit

    override fun onFtp(
        deviceUuid: String,
        payload: String,
    ) = Unit

    override fun onClipboard(payload: String) = Unit

    override fun onStatus(
        deviceUuid: String,
        payload: String,
    ) = Unit

    override fun onAppLaunch(
        device: DeviceInfo,
        payload: String,
    ) = Unit

    override fun onSuperIsland(
        deviceUuid: String,
        payload: String,
    ) = Unit
}
