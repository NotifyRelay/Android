package notifyrelay.base.util

inline fun <T> measureTime(tag: String, operation: String, block: () -> T): T {
    val start = System.nanoTime()
    return try {
        block()
    } finally {
        val duration = (System.nanoTime() - start) / 1_000_000
        if (duration > 16) {
            Logger.w(tag, "$operation 耗时 ${duration}ms")
        }
    }
}
