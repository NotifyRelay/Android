package notifyrelay.base.log

import android.util.Log
import timber.log.Timber

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
