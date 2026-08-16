package notifyrelay.base.util

import android.util.Log
import timber.log.Timber

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
                                Log.VERBOSE -> Level.VERBOSE
                                Log.DEBUG -> Level.DEBUG
                                Log.INFO -> Level.INFO
                                Log.WARN -> Level.WARN
                                Log.ERROR -> Level.ERROR
                                else -> return true
                            }
                        return when (currentLevel) {
                            Level.NONE -> false
                            else -> level.ordinal >= currentLevel.ordinal
                        }
                    }
                },
            )
        } catch (_: IllegalStateException) {
        }
    }

    fun plant(tree: Timber.Tree) {
        Timber.plant(tree)
    }

    fun v(
        tag: String,
        message: String,
    ) = Timber.tag(tag).v(message)

    fun v(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = Timber.tag(tag).v(throwable, message)

    fun d(
        tag: String,
        message: String,
    ) = Timber.tag(tag).d(message)

    fun d(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = Timber.tag(tag).d(throwable, message)

    fun i(
        tag: String,
        message: String,
    ) = Timber.tag(tag).i(message)

    fun i(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = Timber.tag(tag).i(throwable, message)

    fun w(
        tag: String,
        message: String,
    ) = Timber.tag(tag).w(message)

    fun w(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = Timber.tag(tag).w(throwable, message)

    fun e(
        tag: String,
        message: String,
    ) = Timber.tag(tag).e(message)

    fun e(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = Timber.tag(tag).e(throwable, message)
}
