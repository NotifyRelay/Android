package notifyrelay.shared.feature.appslist

/** [PinnedAppsPort] 的进程级注册点。 */
object PinnedAppsPortRegistry {
    @Volatile
    var current: PinnedAppsPort? = null
        private set

    fun install(port: PinnedAppsPort) {
        current = port
    }
}
