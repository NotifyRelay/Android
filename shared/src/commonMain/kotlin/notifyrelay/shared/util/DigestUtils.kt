package notifyrelay.shared.util

private const val HEX_DIGITS = "0123456789abcdef"

/**
 * 把字节数组编码为小写十六进制字符串。
 *
 * 每个字节固定两位、全小写。
 */
fun ByteArray.toHex(): String =
    buildString(size * 2) {
        for (byte in this@toHex) {
            val value = byte.toInt() and 0xFF
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0F])
        }
    }

/**
 * 计算字符串的 SHA-256 哈希，返回小写十六进制摘要。
 *
 * 摘要算法由各目标源集实现；图片缓存 key 依赖其稳定输出。
 */
expect fun sha256(input: String): String
