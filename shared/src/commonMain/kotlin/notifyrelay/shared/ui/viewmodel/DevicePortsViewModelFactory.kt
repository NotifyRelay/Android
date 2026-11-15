package notifyrelay.shared.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewmodel.compose.viewModel
import notifyrelay.domain.feature.device.service.contract.DevicePortsProvider

/** 设备端口提供者的注入点。 */
val LocalDevicePortsProvider = staticCompositionLocalOf<DevicePortsProvider?> { null }

/** 以已注入的 [DevicePortsProvider] 构造 [DevicePortsViewModel]。 */
@Composable
fun devicePortsViewModel(): DevicePortsViewModel {
    val provider = LocalDevicePortsProvider.current ?: error("LocalDevicePortsProvider 未提供")
    return viewModel(factory = factoryOf { DevicePortsViewModel(provider) })
}
