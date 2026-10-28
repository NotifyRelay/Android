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
 * 消息与聊天能力不属于桌面端当前范围，以查询视图与空实现满足契约。
 */
class DesktopDevicePortsProvider(
    private val manager: DesktopDeviceManager,
) : DevicePortsProvider {
    override fun query(): DeviceQueryPort = manager

    override fun admin(): DeviceAdminPort = manager

    override fun message(): DeviceMessagePort = DesktopMessagePort(manager)

    override fun chat(): DeviceChatPort = DesktopChatPort(manager)
}

/** 桌面端消息端口：只有设备视图与认证表，无发送链路。 */
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

    override fun authenticatedSnapshot(): Map<String, AuthInfo> = manager.getAuthenticatedDevices()

    override val localUuid: String get() = manager.localUuid
}

/** 桌面端聊天端口：在消息端口之上补一个空的入站通知订阅。 */
private class DesktopChatPort(
    private val manager: DesktopDeviceManager,
) : DeviceChatPort {
    private val callbacks = mutableSetOf<(String) -> Unit>()

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

    override fun authenticatedSnapshot(): Map<String, AuthInfo> = manager.getAuthenticatedDevices()

    override val localUuid: String get() = manager.localUuid

    override fun registerOnNotificationDataReceived(callback: (String) -> Unit) {
        callbacks.add(callback)
    }

    override fun unregisterOnNotificationDataReceived(callback: (String) -> Unit) {
        callbacks.remove(callback)
    }
}
