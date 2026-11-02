package notifyrelay.base.log

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fileStamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

/** 格式化为单行文本：时间 + 级别 + tag + 消息（含异常栈）。 */
private fun formatLine(
    letter: String,
    tag: String?,
    message: String,
    throwable: Throwable?,
): String =
    buildString {
        append(fileStamp.format(Date()))
        append(' ')
        append(letter)
        append('/')
        append(tag ?: "?")
        append(": ")
        append(message)
        if (throwable != null) {
            append('\n')
            append(Log.getStackTraceString(throwable))
        }
    }

/** 安装日志文件通道。应在应用启动最早阶段调用一次。 */
fun installLoggerFileSink(context: Context) {
    val base = context.getExternalFilesDir(null) ?: context.filesDir
    LogFileStore.install(File(base, "logs").absolutePath)
}

// 日志分发唯一安装点：首次输出日志时安装 DebugTree，级别过滤读取 Logger.currentLevel。
private object TimberInstaller {
    init {
        try {
            Timber.plant(
                object : Timber.DebugTree() {
                    override fun isLoggable(
                        tag: String?,
                        priority: Int,
                    ): Boolean {
                        val level =
                            when (priority) {
                                Log.VERBOSE -> Logger.Level.VERBOSE
                                Log.DEBUG -> Logger.Level.DEBUG
                                Log.INFO -> Logger.Level.INFO
                                Log.WARN -> Logger.Level.WARN
                                Log.ERROR -> Logger.Level.ERROR
                                else -> return true
                            }
                        return when (Logger.currentLevel) {
                            Logger.Level.NONE -> false
                            else -> level.ordinal >= Logger.currentLevel.ordinal
                        }
                    }
                },
            )
            Timber.plant(
                object : Timber.Tree() {
                    override fun log(
                        priority: Int,
                        tag: String?,
                        message: String,
                        t: Throwable?,
                    ) {
                        val letter =
                            when (priority) {
                                Log.VERBOSE -> "V"
                                Log.DEBUG -> "D"
                                Log.INFO -> "I"
                                Log.WARN -> "W"
                                Log.ERROR -> "E"
                                else -> "?"
                            }
                        // 文件通道记录全部级别，不受 Logger.currentLevel 过滤影响，
                        // 保证现场取证时能拿到完整链路。
                        LogFileStore.write(formatLine(letter, tag, message, t))
                    }
                },
            )
        } catch (_: IllegalStateException) {
        }
    }

    fun ensureInstalled() = Unit
}

internal actual fun platformLogV(
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    TimberInstaller.ensureInstalled()
    if (throwable == null) {
        Timber.tag(tag).v(message)
    } else {
        Timber.tag(tag).v(throwable, message)
    }
}

internal actual fun platformLogD(
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    TimberInstaller.ensureInstalled()
    if (throwable == null) {
        Timber.tag(tag).d(message)
    } else {
        Timber.tag(tag).d(throwable, message)
    }
}

internal actual fun platformLogI(
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    TimberInstaller.ensureInstalled()
    if (throwable == null) {
        Timber.tag(tag).i(message)
    } else {
        Timber.tag(tag).i(throwable, message)
    }
}

internal actual fun platformLogW(
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    TimberInstaller.ensureInstalled()
    if (throwable == null) {
        Timber.tag(tag).w(message)
    } else {
        Timber.tag(tag).w(throwable, message)
    }
}

internal actual fun platformLogE(
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    TimberInstaller.ensureInstalled()
    if (throwable == null) {
        Timber.tag(tag).e(message)
    } else {
        Timber.tag(tag).e(throwable, message)
    }
}
