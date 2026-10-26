package notifyrelay.shared.feature.appslist

/** [LocalAppsPort] 的进程级注册点。 */
object LocalAppsPortRegistry {
    @Volatile
    var current: LocalAppsPort? = null
        private set

    fun install(port: LocalAppsPort) {
        current = port
    }
}
