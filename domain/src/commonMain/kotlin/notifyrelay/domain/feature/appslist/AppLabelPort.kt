package notifyrelay.domain.feature.appslist

/**
 * 本机应用显示名端口。
 *
 * 应用列表应答报文需要「包名 → 应用名」，取值依赖平台的包管理器；
 * 桌面端不枚举本机应用，返回包名本身。
 */
interface AppLabelPort {
    /** 取本机应用显示名；未知包名返回包名本身。 */
    fun labelOf(packageName: String): String
}

/** [AppLabelPort] 的注册点。 */
object AppLabelPortRegistry {
    @Volatile
    var current: AppLabelPort? = null
        private set

    fun install(port: AppLabelPort) {
        current = port
    }
}
