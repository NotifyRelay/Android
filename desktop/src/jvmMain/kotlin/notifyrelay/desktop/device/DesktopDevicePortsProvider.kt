package notifyrelay.desktop.device

import notifyrelay.domain.feature.device.model.AuthInfo
import notifyrelay.domain.feature.device.service.contract.DeviceAdminPort
import notifyrelay.domain.feature.device.service.contract.DeviceChatPort
import notifyrelay.domain.feature.device.service.contract.DeviceMessagePort
import notifyrelay.domain.feature.device.service.contract.DevicePortsProvider
import notifyrelay.domain.feature.device.service.contract.DeviceQueryPort

/**
 * 桌面端设备端口提供者。
 *
 * 设备列表与配对共用同一个 [DesktopDeviceManager]；
 * 消息与聊天端口转发到同一个管理器：认证表快照与入站通知回调集合均为同一份。
 */
class DesktopDevicePortsProvider(
    private val manager: DesktopDeviceManager,
) : DevicePortsProvider {
    override fun query(): DeviceQueryPort = manager

    override fun admin(): DeviceAdminPort = manager

    override fun message(): DeviceMessagePort = DesktopMessagePort(manager)

    override fun chat(): DeviceChatPort = DesktopChatPort(manager)
}

/** 桌面端消息端口：设备视图与认证表快照；发送经 [DesktopDeviceManager.sendDataMessage]。 */
private class DesktopMessagePort(
    private val manager: DesktopDeviceManager,
) : DeviceMessagePort {
    override val devices get() = manager.devices

    override fun getAuthenticatedDevices(): Map<String, AuthInfo> = manager.getAuthenticatedDevices()

    override fun getPairedDeviceUuids(): Set<String> = manager.getPairedDeviceUuids()

    override fun getAuthenticatedOnlineCount(): Int = manager.getAuthenticatedOnlineCount()

    override fun getAuthenticatedOnlineDevices() = manager.getAuthenticatedOnlineDevices()

    override fun resolveDeviceInfo(
        uuid: String,
        fallbackIp: String?,
        fallbackPort: Int,
    ) = manager.resolveDeviceInfo(uuid, fallbackIp, fallbackPort)

    override fun authenticatedSnapshot(): Map<String, AuthInfo> = manager.authenticatedSnapshot()

    override val localUuid: String get() = manager.localUuid
}

/** 桌面端聊天端口：在消息端口之上补入站通知订阅（与管理器共用同一回调集合）。 */
private class DesktopChatPort(
    private val manager: DesktopDeviceManager,
) : DeviceChatPort {
    override val devices get() = manager.devices

    override fun getAuthenticatedDevices(): Map<String, AuthInfo> = manager.getAuthenticatedDevices()

    override fun getPairedDeviceUuids(): Set<String> = manager.getPairedDeviceUuids()

    override fun getAuthenticatedOnlineCount(): Int = manager.getAuthenticatedOnlineCount()

    override fun getAuthenticatedOnlineDevices() = manager.getAuthenticatedOnlineDevices()

    override fun resolveDeviceInfo(
        uuid: String,
        fallbackIp: String?,
        fallbackPort: Int,
    ) = manager.resolveDeviceInfo(uuid, fallbackIp, fallbackPort)

    override fun authenticatedSnapshot(): Map<String, AuthInfo> = manager.authenticatedSnapshot()

    override val localUuid: String get() = manager.localUuid

    override fun registerOnNotificationDataReceived(callback: (String) -> Unit) = manager.registerNotificationDataCallback(callback)

    override fun unregisterOnNotificationDataReceived(callback: (String) -> Unit) = manager.unregisterNotificationDataCallback(callback)
}
