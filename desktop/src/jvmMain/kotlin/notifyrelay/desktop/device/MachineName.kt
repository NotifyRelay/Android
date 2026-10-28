package notifyrelay.desktop.device

/**
 * 本机设备名。
 */
object MachineName {
    /** 计算机名；不可用时退化为用户名，再退化为固定串。 */
    fun resolve(): String {
        val host = computerName()
        if (!host.isNullOrBlank()) return host
        val user = userName()
        if (!user.isNullOrBlank()) return user
        return FALLBACK
    }

    /**
     * 读取计算机名（对应 `Environment.MachineName`）。
     *
     * Windows 取 `COMPUTERNAME`，类 Unix 取 `HOSTNAME`；
     */
    private fun computerName(): String? {
        val fromEnv = System.getenv("COMPUTERNAME")
        if (!fromEnv.isNullOrBlank()) return fromEnv
        val fromHost = System.getenv("HOSTNAME")
        if (!fromHost.isNullOrBlank()) return fromHost
        return fallbackHostName()
    }

    private fun fallbackHostName(): String? =
        runCatching {
            val address = java.net.InetAddress.getLocalHost()
            address.hostName
        }.getOrNull()

    /** 读取登录用户名（对应 `Environment.UserName`）。 */
    private fun userName(): String? {
        val fromProp = System.getProperty("user.name")
        if (!fromProp.isNullOrBlank()) return fromProp
        return System.getenv("USERNAME")
    }

    private const val FALLBACK = "NotifyRelay"
}
