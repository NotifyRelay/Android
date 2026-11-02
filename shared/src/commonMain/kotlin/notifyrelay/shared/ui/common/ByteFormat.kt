package notifyrelay.shared.ui.common

/**
 * 字节数转为便于阅读的容量文本。
 *
 * 采用 1024 进制并按量级保留一位小数，仅用于界面展示。
 */
fun bytesToReadable(bytes: Long): String {
    if (bytes < 1024) return bytes.toString() + " B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    val rounded = (value * 10).toLong() / 10.0
    return rounded.toString() + " " + units[unitIndex]
}
