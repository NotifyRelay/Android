package notifyrelay.base.log

import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// JVM 侧日志分发：写 stdout/stderr，并把同一份内容追加到文件。
// 级别过滤读取 Logger.currentLevel；文件通道记录全部级别，供离线排查。
private val out: PrintStream = System.out
private val err: PrintStream = System.err

private val fileStamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

private fun enabled(level: Logger.Level): Boolean =
    when (Logger.currentLevel) {
        Logger.Level.NONE -> false
        else -> level.ordinal >= Logger.currentLevel.ordinal
    }

private fun formatLine(
    letter: String,
    tag: String,
    message: String,
    throwable: Throwable?,
): String =
    buildString {
        append(fileStamp.format(Date()))
        append(' ')
        append(letter)
        append('/')
        append(tag)
        append(": ")
        append(message)
        if (throwable != null) {
            append('\n')
            append(throwable.stackTraceToString())
        }
    }

/** 安装日志文件通道。应在应用启动最早阶段调用一次。 */
fun installLoggerFileSink(directory: String) {
    LogFileStore.install(directory)
}

private fun emit(
    level: Logger.Level,
    letter: String,
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    LogFileStore.write(formatLine(letter, tag, message, throwable))
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
