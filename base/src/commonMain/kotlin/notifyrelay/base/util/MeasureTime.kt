package notifyrelay.base.util

import notifyrelay.base.log.Logger
import kotlin.time.TimeSource

/**
 * 计时并记录超过 16ms 的操作，日志经 notifyrelay.shared.log.Logger 输出。
 */
inline fun <T> measureTime(
    tag: String,
    operation: String,
    block: () -> T,
): T {
    val mark = TimeSource.Monotonic.markNow()
    return try {
        block()
    } finally {
        val duration = mark.elapsedNow().inWholeMilliseconds
        if (duration > 16) {
            Logger.w(tag, "$operation 耗时 ${duration}ms")
        }
    }
}
