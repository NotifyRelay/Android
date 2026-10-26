package notifyrelay.shared.feature.appslist

/** [AppIconPort] 的进程级注册点。平台侧在启动时写入实现。 */
object AppIconPortRegistry {
    @Volatile
    var current: AppIconPort? = null
        private set

    fun install(port: AppIconPort) {
        current = port
    }
}
