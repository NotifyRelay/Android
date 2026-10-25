package notifyrelay.data

/**
 * Set<String> 与 JSON 数组字符串之间的编解码
 * 输出与 Gson 默认 Gson().toJson(Set<String>) 逐字节一致：HTML 字符（< > & = '）与 U+2028/U+2029 均转义，控制字符使用小写 \u00xx
 */
internal object StringSetJsonCodec {
    /**
     * 将字符串集合编码为 JSON 数组字符串，元素顺序与原集合迭代顺序一致
     */
    fun encode(values: Set<String>): String {
        val builder = StringBuilder()
        builder.append('[')
        var first = true
        for (value in values) {
            if (!first) {
                builder.append(',')
            }
            first = false
            appendQuoted(builder, value)
        }
        builder.append(']')
        return builder.toString()
    }

    /**
     * 解析 JSON 数组字符串为字符串集合（保持数组顺序）
     * 空串或 null 字面量返回 null，格式不合法时抛出 IllegalArgumentException
     */
    fun decode(json: String): Set<String>? {
        val text = json.trim()
        if (text.isEmpty() || text == "null") {
            return null
        }
        val parser = Parser(text)
        return parser.parseArray()
    }

    private fun appendQuoted(
        builder: StringBuilder,
        value: String,
    ) {
        builder.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\t' -> builder.append("\\t")
                '\b' -> builder.append("\\b")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\u000C' -> builder.append("\\f")
                '<' -> builder.append("\\u003c")
                '>' -> builder.append("\\u003e")
                '&' -> builder.append("\\u0026")
                '=' -> builder.append("\\u003d")
                '\'' -> builder.append("\\u0027")
                '\u2028' -> builder.append("\\u2028")
                '\u2029' -> builder.append("\\u2029")
                else ->
                    if (ch.code < 0x20) {
                        appendUnicodeEscape(builder, ch.code)
                    } else {
                        builder.append(ch)
                    }
            }
        }
        builder.append('"')
    }

    private fun appendUnicodeEscape(
        builder: StringBuilder,
        code: Int,
    ) {
        builder.append("\\u")
        val hex = code.toString(16)
        repeat(4 - hex.length) {
            builder.append('0')
        }
        builder.append(hex)
    }

    private class Parser(
        private val text: String,
    ) {
        private var index = 0

        fun parseArray(): Set<String> {
            expect('[')
            val result = LinkedHashSet<String>()
            skipWhitespace()
            if (peek() == ']') {
                index++
                finish()
                return result
            }
            while (true) {
                skipWhitespace()
                result.add(parseString())
                skipWhitespace()
                when (peek()) {
                    ',' -> {
                        index++
                    }
                    ']' -> {
                        index++
                        finish()
                        return result
                    }
                    else -> fail("缺少 ',' 或 ']'")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                val ch = next()
                when (ch) {
                    '"' -> return builder.toString()
                    '\\' -> builder.append(parseEscape())
                    else -> builder.append(ch)
                }
            }
        }

        private fun parseEscape(): Char {
            val ch = next()
            return when (ch) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    if (index + 4 > text.length) {
                        fail("\\u 转义不完整")
                    }
                    val hex = text.substring(index, index + 4)
                    index += 4
                    hex.toIntOrNull(16)?.toChar() ?: fail("\\u 转义非法")
                }
                else -> fail("非法转义符")
            }
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) {
                index++
            }
        }

        private fun peek(): Char = if (index < text.length) text[index] else fail("字符串提前结束")

        private fun next(): Char = if (index < text.length) text[index++] else fail("字符串提前结束")

        private fun expect(expected: Char) {
            if (next() != expected) {
                fail("期望 '$expected'")
            }
        }

        private fun finish() {
            skipWhitespace()
            if (index != text.length) {
                fail("数组之后存在多余内容")
            }
        }

        private fun fail(message: String): Nothing = throw IllegalArgumentException("JSON 数组解析失败：$message（偏移 $index）")
    }
}
