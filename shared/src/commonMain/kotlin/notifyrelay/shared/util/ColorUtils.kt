package notifyrelay.shared.util

/**
 * 颜色解析工具（平台无关）。
 */
object ColorUtils {
    /**
     * 解析十六进制颜色文本，返回 ARGB 整数。
     *
     * 支持 `#RGB`、`#ARGB`、`#RRGGBB`、`#AARRGGBB` 四种写法；其余输入返回 null。
     *
     * @param colorString 形如 `#FF8800` 的颜色文本，可为 null。
     * @return ARGB 整数；无法解析时返回 null。
     */
    fun parseColor(colorString: String?): Int? {
        val text = colorString?.trim() ?: return null
        if (text.length < 2 || text[0] != '#') return null
        val hex = text.substring(1)
        val expanded =
            when (hex.length) {
                3, 4 -> buildString { for (ch in hex) append(ch).append(ch) }
                6, 8 -> hex
                else -> return null
            }
        if (!expanded.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
        val value = expanded.toLongOrNull(16) ?: return null
        return if (expanded.length == 6) (value or 0xFF000000L).toInt() else value.toInt()
    }

    /**
     * 解析不带 `#` 前缀的十六进制颜色文本，按不透明色（`0xFF000000 or 值`）返回。
     *
     * 用于超级岛协议直接携带 `RRGGBB` / `AARRGGBB` 文本的场景；空白或非法输入返回 [fallback]。
     *
     * @param colorString 十六进制文本，可为 null。
     * @param fallback 无法解析时返回的兜底颜色。
     * @return ARGB 整数。
     */
    fun parseColorOr(
        colorString: String?,
        fallback: Int,
    ): Int {
        if (colorString.isNullOrBlank()) return fallback
        val parsed = colorString.toIntOrNull(16) ?: return fallback
        return 0xFF000000.toInt() or parsed
    }
}
