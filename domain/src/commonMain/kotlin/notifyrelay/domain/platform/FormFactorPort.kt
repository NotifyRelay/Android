package notifyrelay.domain.platform

/**
 * 设备形态与系统版本端口。
 *
 * 公共配置策略需要「是否平板」与「系统版本」两项判定，
 * 两者在 Android 依赖 Context / Build 指纹，故经本端口注入；
 * 桌面端按固定值返回（非平板、无系统版本）。
 */
interface FormFactorPort {
    /** 是否平板形态。 */
    fun isTablet(): Boolean

    /** 系统版本串（形如 "OS3.0.300"）；无法判定时返回 null。 */
    fun osVersion(): String?
}

/** [FormFactorPort] 的注册点。 */
object FormFactorPortRegistry {
    @Volatile
    var current: FormFactorPort? = null
        private set

    fun install(port: FormFactorPort) {
        current = port
    }
}
