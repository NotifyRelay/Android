package notifyrelay.base.util

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
