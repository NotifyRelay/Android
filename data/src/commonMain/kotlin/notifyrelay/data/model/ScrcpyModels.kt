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

data class SelectedDeviceInfo(
    val displayName: String,
    val ip: String,
    val deviceType: String? = null,
)
