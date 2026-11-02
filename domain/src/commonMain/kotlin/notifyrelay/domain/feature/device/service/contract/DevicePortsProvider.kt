package notifyrelay.domain.feature.device.service.contract

/** 设备端口提供者。平台侧实现并注入。 */
interface DevicePortsProvider {
    fun query(): DeviceQueryPort

    fun admin(): DeviceAdminPort

    fun message(): DeviceMessagePort

    fun chat(): DeviceChatPort
}
