package notifyrelay.data.model

data class ConnectionTarget(
    val host: String,
    val port: Int,
)

data class OnlineDeviceInfo(
    val uuid: String,
    val displayName: String,
    val ip: String,
    val port: Int,
    val deviceType: String? = null,
)

/**
 * 已选设备展示信息：唯一来源为 [notifyrelay.domain.feature.device.model.SelectedDeviceInfo]。
 *
 * 保留本包内名称供已按 `notifyrelay.data.model.SelectedDeviceInfo` 编译的消费方
 * （如 :scrcpy 的 ScrcpyScreenHost）继续解析。
 */
typealias SelectedDeviceInfo = notifyrelay.domain.feature.device.model.SelectedDeviceInfo
