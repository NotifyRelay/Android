package notifyrelay.shared.util

import kotlin.time.TimeSource

/**
 * 公共端 WARN 级日志出口。
 *
 * platform 无关源码不能依赖 Timber 等平台日志库，由各目标源集提供实现。
 */
@PublishedApi
internal expect fun sharedLogW(
    tag: String,
    message: String,
)

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
            sharedLogW(tag, "$operation 耗时 ${duration}ms")
        }
    }
}
