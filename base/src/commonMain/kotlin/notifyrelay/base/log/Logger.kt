package notifyrelay.base.log

/**
 * 全仓唯一日志入口。
 *
 * 级别状态（currentLevel）与过滤通知日志开关在此持有；输出通道由各目标源集的
 * platformLogXxx 提供，对外 API 为 5 个级别 × 2 个重载。
 */
object Logger {
    enum class Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        NONE,
    }

    var currentLevel = Level.INFO

    // 过滤通知日志开关（默认开启）
    var enableFilteredNotificationLog = true

    fun v(
        tag: String,
        message: String,
    ) = platformLogV(tag, message)

    fun v(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = platformLogV(tag, message, throwable)

    fun d(
        tag: String,
        message: String,
    ) = platformLogD(tag, message)

    fun d(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = platformLogD(tag, message, throwable)

    fun i(
        tag: String,
        message: String,
    ) = platformLogI(tag, message)

    fun i(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = platformLogI(tag, message, throwable)

    fun w(
        tag: String,
        message: String,
    ) = platformLogW(tag, message)

    fun w(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = platformLogW(tag, message, throwable)

    fun e(
        tag: String,
        message: String,
    ) = platformLogE(tag, message)

    fun e(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = platformLogE(tag, message, throwable)
}

// 平台日志实现：throwable 为 null 时按 2 参形式输出。
internal expect fun platformLogV(
    tag: String,
    message: String,
    throwable: Throwable? = null,
)

internal expect fun platformLogD(
    tag: String,
    message: String,
    throwable: Throwable? = null,
)

internal expect fun platformLogI(
    tag: String,
    message: String,
    throwable: Throwable? = null,
)

internal expect fun platformLogW(
    tag: String,
    message: String,
    throwable: Throwable? = null,
)

internal expect fun platformLogE(
    tag: String,
    message: String,
    throwable: Throwable? = null,
)
