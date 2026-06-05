package notifyrelay.base.util

inline fun <T> measureTime(tag: String, operation: String, block: () -> T): T {
    val start = System.currentTimeMillis()
    val result = block()
    val duration = System.currentTimeMillis() - start
    if (duration > 16) {
        Logger.w(tag, "$operation 耗时 ${duration}ms")
    }
    return result
}
