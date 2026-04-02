package github.xzynine.superislandui.common

import kotlin.math.min
import notifyrelay.base.util.Logger

/**
 * 性能监控工具
 */
private inline fun <T> measureTime(operation: String, block: () -> T): T {
    val start = System.currentTimeMillis()
    val result = block()
    val duration = System.currentTimeMillis() - start
    if (duration > 16) { // 超过一帧时间
        Logger.w("TextSplitter", "$operation 耗时 ${duration}ms")
    }
    return result
}

/**
 * 文本拆分工具类，用于处理歌词等文本的拆分
 */
object TextSplitter {
    
    private val HALF_WIDTH_PUNCTUATION = setOf(
        ',', '.', '!', '?', ';', ':', '-', '_', '(', ')', '[', ']', '{', '}', 
        '<', '>', '"', '\'', '`', '~', '|', '\\', '/', '%', '^', '&', '*', '+', '='
    )
    
    private val PUNCTUATION = setOf(
        '，', '。', '！', '？', '；', '：', ',', '.', '!', '?', ';', ':', '、'
    )
    
    // 字符类型枚举
    private enum class CharType {
        CHINESE, KANA, LOWERCASE, DIGIT, PUNCTUATION, OTHER
    }
    
    // 字符类型缓存
    private val charTypeCache = mutableMapOf<Char, CharType>()
    
    /**
     * 计算文本的中文字符等价长度
     * 2个英语字符视为1个中文字符
     * 2个数字视为1个中文字符
     * 2个小写西里尔字母视为1个中文字符
     * 2个半角标点视为1个中文字符
     * 日语片假名视为1个中文字符
     */
    fun calculateTextLength(text: String): Double {
        return measureTime("calculateTextLength") {
            var length = 0.0
            for (char in text) {
                length += getCharWeight(char)
            }
            return@measureTime length
        }
    }
    
    /**
     * 获取字符的等价权重
     * @return 1.0 表示全角字符，0.5 表示半角字符
     */
    private fun getCharWeight(c: Char): Double {
        return when (getCharType(c)) {
            CharType.CHINESE, CharType.KANA, CharType.OTHER -> 1.0
            CharType.LOWERCASE, CharType.DIGIT -> 0.5
            CharType.PUNCTUATION -> if (c in HALF_WIDTH_PUNCTUATION) 0.5 else 1.0
        }
    }
    
    /**
     * 判断字符是否为小写字母（包括英语和西里尔）
     */
    private fun isLowercaseLetter(c: Char): Boolean {
        return c in 'a'..'z' || c in 'а'..'я'
    }
    
    /**
     * 判断字符是否为中文字符
     * 使用 Unicode 范围比较，比 Character.UnicodeBlock.of() 更高效
     */
    private fun isChineseCharacter(c: Char): Boolean {
        val code = c.code
        return code in 0x4E00..0x9FFF ||
            code in 0x3400..0x4DBF ||
            code in 0x20000..0x2A6DF ||
            code in 0xF900..0xFAFF ||
            code in 0x2F800..0x2FA1F
    }
    
    /**
     * 判断字符是否为日语片假名/平假名
     * 使用 Unicode 范围比较，比 Character.UnicodeBlock.of() 更高效
     */
    private fun isKanaCharacter(c: Char): Boolean {
        val code = c.code
        return code in 0x3040..0x309F ||
            code in 0x30A0..0x30FF ||
            code in 0x31F0..0x31FF
    }
    
    /**
     * 判断字符是否为半角标点符号
     */
    private fun isHalfWidthPunctuation(char: Char): Boolean {
        return char in HALF_WIDTH_PUNCTUATION
    }
    
    /**
     * 判断字符是否为标点符号
     */
    private fun isPunctuation(char: Char): Boolean {
        return char in PUNCTUATION
    }
    
    /**
     * 获取字符类型，使用缓存避免重复计算
     */
    private fun getCharType(c: Char): CharType {
        return charTypeCache.getOrPut(c) {
            when {
                isChineseCharacter(c) -> CharType.CHINESE
                isKanaCharacter(c) -> CharType.KANA
                isLowercaseLetter(c) -> CharType.LOWERCASE
                c.isDigit() -> CharType.DIGIT
                isPunctuation(c) -> CharType.PUNCTUATION
                else -> CharType.OTHER
            }
        }
    }
    
    /**
     * 截断文本，最长13等价字符，允许最多超出5个字符
     * @param text 原始文本
     * @return 截断后的文本
     */
    fun truncateText(text: String): String {
        return measureTime("truncateText") {
            val maxEquivalentLength = 13.0
            val maxAllowedLength = 18
            
            if (text.isEmpty()) {
                return@measureTime text
            }
            
            if (text.length <= maxAllowedLength) {
                return@measureTime text
            }
            
            var currentLength = 0.0
            var truncatePoint = 0
            
            for (i in 0 until text.length) {
                currentLength += getCharWeight(text[i])
                
                if (currentLength >= maxEquivalentLength) {
                    truncatePoint = i + 1
                    break
                }
            }
            
            if (truncatePoint == 0) {
                truncatePoint = maxAllowedLength
            }
            
            // 优化边界检查，减少函数调用
            if (truncatePoint > maxAllowedLength) truncatePoint = maxAllowedLength
            if (truncatePoint < 1) truncatePoint = 1
            
            var finalTruncatePoint = truncatePoint
            val end = minOf(text.length, truncatePoint + 5)
            for (i in truncatePoint until end) {
                if (text[i] == ' ' || isPunctuation(text[i])) {
                    finalTruncatePoint = i
                    break
                }
            }
            
            if (finalTruncatePoint < 1) finalTruncatePoint = 1
            
            return@measureTime text.substring(0, finalTruncatePoint)
        }
    }

    /**
     * 拆分歌词文本，考虑字符类型的长度计算
     * @param lyricText 歌词文本
     * @param threshold 拆分阈值（中文字符等价长度）
     * @return Pair(图标文本, 胶囊文本)
     */
    fun splitLyric(lyricText: String, threshold: Int): Pair<String, String> {
        return measureTime("splitLyric") {
            val truncatedText = truncateText(lyricText)
            
            if (truncatedText.isEmpty()) {
                return@measureTime Pair("", "")
            }
            
            // 预计算字符权重数组，避免多次遍历
            val charWeights = truncatedText.map { getCharWeight(it) }
            val textLength = charWeights.sum()
            
            if (textLength <= threshold) {
                return@measureTime Pair("", truncatedText)
            }
            
            val capsuleEquivalentLength = 6.0
            var capsuleSplitPoint = truncatedText.length
            
            var currentLength = 0.0
            for (i in truncatedText.length - 1 downTo 0) {
                currentLength += charWeights[i]
                
                if (currentLength >= capsuleEquivalentLength) {
                    capsuleSplitPoint = i
                    break
                }
            }
            
            val maxIconEquivalentLength = 7.0
            var iconSplitPoint = min(capsuleSplitPoint, truncatedText.length)
            
            currentLength = 0.0
            for (i in 0 until capsuleSplitPoint) {
                currentLength += charWeights[i]
                
                if (currentLength >= maxIconEquivalentLength) {
                    iconSplitPoint = i + 1
                    break
                }
            }
            
            val minSplitPoint = 2
            iconSplitPoint = maxOf(minSplitPoint, iconSplitPoint)
            iconSplitPoint = min(iconSplitPoint, truncatedText.length)
            
            val safeIconSplitPoint = min(iconSplitPoint, truncatedText.lastIndex.coerceAtLeast(0))
            
            val searchStart = minSplitPoint
            val searchEnd = minOf(capsuleSplitPoint, iconSplitPoint + 3, truncatedText.length)
            
            var splitPoint = safeIconSplitPoint
            var foundSplitPoint = false
            if (truncatedText.isNotEmpty() && safeIconSplitPoint >= searchStart) {
                for (i in safeIconSplitPoint downTo searchStart) {
                    if (truncatedText[i] == ' ' || isPunctuation(truncatedText[i])) {
                        splitPoint = i
                        foundSplitPoint = true
                        break
                    }
                }
            }
            
            if (!foundSplitPoint && truncatedText.isNotEmpty() && safeIconSplitPoint < searchEnd) {
                for (i in safeIconSplitPoint until searchEnd) {
                    if (truncatedText[i] == ' ' || isPunctuation(truncatedText[i])) {
                        splitPoint = i
                        break
                    }
                }
            }
            
            var finalSplitPoint = maxOf(minSplitPoint, min(splitPoint, capsuleSplitPoint))
            
            // 检查分割点是否是标点符号，且是否是图标文本的第一个字符
            // 如果是，将标点符号移到图标区
            if (finalSplitPoint < truncatedText.length && isPunctuation(truncatedText[finalSplitPoint])) {
                // 检查图标文本是否为空或只有一个字符，或者图标文本未达到上限
                val maxIconEquivalentLength = 7.0
                val currentIconLength = truncatedText.take(finalSplitPoint).sumOf { getCharWeight(it) }
                
                if (finalSplitPoint <= 1 || currentIconLength < maxIconEquivalentLength) {
                    // 如果图标文本为空或只有一个字符，或者图标文本未达到上限，将标点符号移到图标区
                    finalSplitPoint = min(finalSplitPoint + 1, truncatedText.length)
                }
            }
            
            val iconText = truncatedText.take(finalSplitPoint)
            val capsuleText = truncatedText.substring(finalSplitPoint)
            
            return@measureTime Pair(iconText, capsuleText)
        }
    }
}
