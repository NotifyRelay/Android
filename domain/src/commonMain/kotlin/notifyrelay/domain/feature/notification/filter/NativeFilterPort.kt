package notifyrelay.domain.feature.notification.filter

/**
 * Rust 过滤能力端口。
 *
 * `:data` 不能依赖 `:core`（`:core` 已 `api` 依赖 `:data`，反向会成环），
 * 故过滤配置与 Rust 的交互经本端口注入；实现位于平台侧。
 *
 * 未注册实现时调用方按 fail-open 处理：不做过滤、包名映射退化为原包名。
 */
interface NativeFilterPort {
    /** Rust core 是否就绪。 */
    fun isReady(): Boolean

    /** 同步过滤配置；成功返回 true。 */
    fun setFilterConfig(json: String): Boolean

    /** 把远端包名映射到本机等价包名；无法映射时返回 null。 */
    fun mapLocalPackage(packageName: String): String?

    /** 按当前过滤模式判定是否放行；未就绪时应返回 true。 */
    fun checkFilterMode(
        packageName: String,
        notificationPackageName: String,
        title: String,
        text: String,
    ): Boolean
}

/** [NativeFilterPort] 的注册点。 */
object NativeFilterPortRegistry {
    @Volatile
    var current: NativeFilterPort? = null
        private set

    fun install(port: NativeFilterPort) {
        current = port
    }
}
