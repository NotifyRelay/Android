package notifyrelay.shared.ui.viewmodel

import androidx.lifecycle.ViewModel
import notifyrelay.domain.feature.device.service.contract.DeviceAdminPort
import notifyrelay.domain.feature.device.service.contract.DeviceChatPort
import notifyrelay.domain.feature.device.service.contract.DeviceMessagePort
import notifyrelay.domain.feature.device.service.contract.DevicePortsProvider
import notifyrelay.domain.feature.device.service.contract.DeviceQueryPort

/**
 * 设备端口获取的承载（平台无关）。
 *
 * 端口实例由 [DevicePortsProvider] 在平台侧提供。
 */
class DevicePortsViewModel(
    private val provider: DevicePortsProvider,
) : ViewModel() {
    fun queryPort(): DeviceQueryPort = provider.query()

    fun adminPort(): DeviceAdminPort = provider.admin()

    fun messagePort(): DeviceMessagePort = provider.message()

    fun chatPort(): DeviceChatPort = provider.chat()
}
