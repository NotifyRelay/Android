package notifyrelay.base.util

import notifyrelay.shared.log.Logger as SharedLogger

// notifyrelay.base.util 侧的兼容外壳：API 形状与枚举顺序同 notifyrelay.shared.log.Logger，
// 状态与输出通道均由 :shared 唯一持有。
object Logger {
    enum class Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        NONE,
    }

    // 级别状态在 :shared，按枚举序号双向映射
    var currentLevel: Level
        get() = Level.entries[SharedLogger.currentLevel.ordinal]
        set(value) {
            SharedLogger.currentLevel = SharedLogger.Level.entries[value.ordinal]
        }

    // 过滤通知日志开关（默认开启），状态在 :shared
    var enableFilteredNotificationLog: Boolean
        get() = SharedLogger.enableFilteredNotificationLog
        set(value) {
            SharedLogger.enableFilteredNotificationLog = value
        }

    fun v(
        tag: String,
        message: String,
    ) = SharedLogger.v(tag, message)

    fun v(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = SharedLogger.v(tag, message, throwable)

    fun d(
        tag: String,
        message: String,
    ) = SharedLogger.d(tag, message)

    fun d(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = SharedLogger.d(tag, message, throwable)

    fun i(
        tag: String,
        message: String,
    ) = SharedLogger.i(tag, message)

    fun i(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = SharedLogger.i(tag, message, throwable)

    fun w(
        tag: String,
        message: String,
    ) = SharedLogger.w(tag, message)

    fun w(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = SharedLogger.w(tag, message, throwable)

    fun e(
        tag: String,
        message: String,
    ) = SharedLogger.e(tag, message)

    fun e(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = SharedLogger.e(tag, message, throwable)
}
