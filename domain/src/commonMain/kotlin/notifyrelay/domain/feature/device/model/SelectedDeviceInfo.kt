package notifyrelay.domain.feature.device.model

/** 已选设备的展示信息。 */
data class SelectedDeviceInfo(
    val displayName: String,
    val ip: String,
    val deviceType: String? = null,
)
