package notifyrelay.desktop.device

import com.xzyht.notifyrelay.nativecore.device.appsync.AppListSyncManagerCore
import com.xzyht.notifyrelay.nativecore.device.appsync.IconSyncManagerCore
import com.xzyht.notifyrelay.nativecore.device.service.callback.DataMessagePort
import notifyrelay.desktop.DataLayerDesktop
import notifyrelay.domain.feature.device.model.DeviceInfo

/**
 * 桌面端入站数据报文端口。
 *
 * 通知报文广播到管理器回调集合；应用列表与图标报文交由公共同步管理器处理；
 * 媒体、剪贴板、状态与超级岛报文在桌面端尚无业务处理，按空实现忽略。
 */
internal class DesktopDataMessagePort(
    private val manager: DesktopDeviceManager,
    private val messagePort: notifyrelay.domain.feature.device.service.contract.DeviceMessagePort,
    private val notificationHandler: DesktopInboundNotificationHandler,
) : DataMessagePort {
    override fun onNotification(
        deviceUuid: String,
        payload: String,
    ) {
        // 回调广播（聊天测试订阅方）与落库归档并行推进，两者互不阻塞
        manager.dispatchNotification(payload)
        notificationHandler.dispatch(payload, deviceUuid)
    }

    override fun onMediaPlay(
        device: DeviceInfo,
        payload: String,
    ) {
        // 远端播放状态回推：解析后更新媒体端口，供音乐控制页展示
        DesktopMediaRemoteHandler.onMediaPlay(device, payload)
    }

    override fun onIconRequest(
        device: DeviceInfo,
        payload: String,
    ) {
        // 本机无应用图标来源，仍必须回包（全部纳入 missing），否则对端 pending 要等到超时
        IconSyncManagerCore.onInboundIconRequest(payload, messagePort, device)
    }

    override fun onIconResponse(payload: String) {
        IconSyncManagerCore.onInboundIconResponse(payload)
    }

    override fun onAppListRequest(
        device: DeviceInfo,
        payload: String,
    ) {
        // 本机应用不列出，应答为空列表而不是不回包
        AppListSyncManagerCore.onInboundAppListRequest(payload, messagePort, device)
    }

    override fun onAppListResponse(
        deviceUuid: String,
        payload: String,
    ) {
        AppListSyncManagerCore.onInboundAppListResponse(payload, deviceUuid, messagePort)
    }

    override fun onMediaControl(
        deviceUuid: String,
        payload: String,
    ) {
        // 远端下发的媒体控制指令：以系统媒体键在本机执行
        DesktopMediaRemoteHandler.onMediaControl(payload)
    }

    override fun onFtp(
        deviceUuid: String,
        payload: String,
    ) = Unit

    override fun onClipboard(payload: String) {
        // 远端剪贴板写回系统剪贴板（Rust 已登记防循环窗口，回写不会再被当作本机变更发出）
        DataLayerDesktop.clipboardSync?.onReceived(payload)
    }

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
