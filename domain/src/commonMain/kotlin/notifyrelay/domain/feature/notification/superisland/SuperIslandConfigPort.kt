package notifyrelay.domain.feature.notification.superisland

/** 规范信息注入方式。 */
enum class SpecInjectionMode {
    /** 仅超级岛规范信息注入。 */
    SUPER_ISLAND,

    /** 仅 Live Updates 规范信息注入。 */
    LIVE_UPDATES,

    /** 都不注入。 */
    NONE,
}

/**
 * 超级岛配置端口。
 *
 * 显示开关、浮窗兼容、列表模式与注入方式均经本接口；实现位于平台侧。
 */
interface SuperIslandConfigPort {
    /** 是否显示远端超级岛。 */
    fun isRemoteDisplayEnabled(): Boolean

    /** 设置远端超级岛显示。 */
    fun setRemoteDisplayEnabled(enabled: Boolean)

    /** 浮窗兼容是否开启。 */
    fun isFloatingWindowEnabled(): Boolean

    /** 设置浮窗兼容；返回是否生效。 */
    fun setFloatingWindowEnabled(enabled: Boolean): Boolean

    /** 是否列表模式。 */
    fun isNotificationListMode(): Boolean

    /** 设置列表模式；返回是否生效。 */
    fun setNotificationListMode(enabled: Boolean): Boolean

    /** 当前注入方式。 */
    fun specInjectionMode(): SpecInjectionMode

    /** 设置注入方式。 */
    fun setSpecInjectionMode(mode: SpecInjectionMode)

    /** 撤下所有已展示的远端超级岛。 */
    fun dismissAllRemote()

    /** 切换超级岛通道。 */
    fun switchChannel()
}

/** [SuperIslandConfigPort] 的注册点。 */
object SuperIslandConfigPortRegistry {
    @Volatile
    var current: SuperIslandConfigPort? = null
        private set

    fun install(port: SuperIslandConfigPort) {
        current = port
    }
}
