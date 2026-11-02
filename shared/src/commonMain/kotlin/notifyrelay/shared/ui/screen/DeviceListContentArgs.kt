package notifyrelay.shared.ui.screen

import notifyrelay.domain.feature.device.model.DeviceInfo

/** 设备列表内容的渲染参数。 */
data class DeviceListContentArgs(
    val isLandscape: Boolean,
    val discoveryEnabled: Boolean,
    val onDiscoveryChange: (Boolean) -> Unit,
    val allDevices: List<DeviceInfo?>,
    val authedDeviceUuids: Set<String>,
    val deviceStates: Map<String, Boolean>,
    val unauthedDevices: List<DeviceInfo>,
    val selectedDevice: DeviceInfo?,
    val onSelectDevice: (DeviceInfo?) -> Unit,
    val onRequestDelete: (DeviceInfo) -> Unit,
    val onShowRejectedDialog: () -> Unit,
    val localBatteryLevel: Int,
)
