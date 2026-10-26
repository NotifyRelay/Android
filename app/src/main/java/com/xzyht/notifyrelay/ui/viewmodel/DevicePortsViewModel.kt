package com.xzyht.notifyrelay.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
import notifyrelay.shared.feature.device.service.contract.DeviceAdminPort
import notifyrelay.shared.feature.device.service.contract.DeviceChatPort
import notifyrelay.shared.feature.device.service.contract.DeviceMessagePort
import notifyrelay.shared.feature.device.service.contract.DeviceQueryPort

/**
 * 设备端口获取的承载。
 *
 * 界面通过本 ViewModel 取端口，不再直接访问 [DeviceConnectionManagerSingleton]。
 */
class DevicePortsViewModel : ViewModel() {
    fun queryPort(context: Context): DeviceQueryPort = DeviceConnectionManagerSingleton.getDeviceQuery(context)

    fun adminPort(context: Context): DeviceAdminPort = DeviceConnectionManagerSingleton.getDeviceAdmin(context)

    fun messagePort(context: Context): DeviceMessagePort = DeviceConnectionManagerSingleton.getDeviceMessage(context)

    fun chatPort(context: Context): DeviceChatPort = DeviceConnectionManagerSingleton.getDeviceChat(context)
}
