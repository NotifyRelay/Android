package notifyrelay.base.util

import java.security.MessageDigest

/**
 * 把字节数组编码为小写十六进制字符串。
 *
 * 与项目中既有的两种等价写法输出一致：
 * `joinToString("") { "%02x".format(it) }` 与
 * `joinToString("") { b -> ((b.toInt() and 0xFF).toString(16)).padStart(2, '0') }`。
 *
 * @return 每个字节固定两位、全小写的十六进制文本。
 */
fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/**
 * 计算字符串的 SHA-256 哈希，返回小写十六进制摘要。
 *
 * 与原 `DiffSystem.sha256` 算法完全一致（MessageDigest("SHA-256") + toHex），
 * 图片缓存 key 依赖其稳定输出。
 */
fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.toHex()
}
