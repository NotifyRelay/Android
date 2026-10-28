package notifyrelay.base.log

import java.io.PrintStream

// JVM 侧日志分发：直接写 stdout/stderr，级别过滤读取 Logger.currentLevel。
// 与 Android 侧 Timber 通道语义对齐（tag + 消息 + 可选异常栈）。
private val out: PrintStream = System.out
private val err: PrintStream = System.err

private fun enabled(level: Logger.Level): Boolean =
    when (Logger.currentLevel) {
        Logger.Level.NONE -> false
        else -> level.ordinal >= Logger.currentLevel.ordinal
    }

private fun emit(
    level: Logger.Level,
    letter: String,
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    if (!enabled(level)) return
    val stream = if (level.ordinal >= Logger.Level.WARN.ordinal) err else out
    stream.println(letter + "/" + tag + ": " + message)
    throwable?.printStackTrace(stream)
}

internal actual fun platformLogV(
    tag: String,
    message: String,
    throwable: Throwable?,
) = emit(Logger.Level.VERBOSE, "V", tag, message, throwable)

internal actual fun platformLogD(
    tag: String,
    message: String,
    throwable: Throwable?,
) = emit(Logger.Level.DEBUG, "D", tag, message, throwable)

internal actual fun platformLogI(
    tag: String,
    message: String,
    throwable: Throwable?,
) = emit(Logger.Level.INFO, "I", tag, message, throwable)

internal actual fun platformLogW(
    tag: String,
    message: String,
    throwable: Throwable?,
) = emit(Logger.Level.WARN, "W", tag, message, throwable)

internal actual fun platformLogE(
    tag: String,
    message: String,
    throwable: Throwable?,
) = emit(Logger.Level.ERROR, "E", tag, message, throwable)
