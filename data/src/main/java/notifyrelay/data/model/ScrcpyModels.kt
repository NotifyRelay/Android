package notifyrelay.data.model

data class ConnectionTarget(
    val host: String,
    val port: Int,
)

data class DeviceShortcut(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val online: Boolean,
)
