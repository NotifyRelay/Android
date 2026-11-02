package com.xzyht.notifyrelay.feature.device.service

import android.content.Context
import notifyrelay.domain.feature.device.service.contract.DeviceAdminPort
import notifyrelay.domain.feature.device.service.contract.DeviceChatPort
import notifyrelay.domain.feature.device.service.contract.DeviceMessagePort
import notifyrelay.domain.feature.device.service.contract.DevicePortsProvider
import notifyrelay.domain.feature.device.service.contract.DeviceQueryPort

/** 以 [DeviceConnectionManagerSingleton] 实现 [DevicePortsProvider]。 */
class AndroidDevicePortsProvider(
    private val context: Context,
) : DevicePortsProvider {
    override fun query(): DeviceQueryPort = DeviceConnectionManagerSingleton.getDeviceQuery(context)

    override fun admin(): DeviceAdminPort = DeviceConnectionManagerSingleton.getDeviceAdmin(context)

    override fun message(): DeviceMessagePort = DeviceConnectionManagerSingleton.getDeviceMessage(context)

    override fun chat(): DeviceChatPort = DeviceConnectionManagerSingleton.getDeviceChat(context)
}
