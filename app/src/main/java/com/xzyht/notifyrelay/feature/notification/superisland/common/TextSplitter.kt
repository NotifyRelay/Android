package com.xzyht.notifyrelay.feature.notification.superisland.common

import kotlin.math.min

/**
 * 文本拆分工具类，用于处理歌词等文本的拆分
 */
object TextSplitter {
    
    /**
     * 计算文本的中文字符等价长度
     * 2个英语字符视为1个中文字符
     * 2个数字视为1个中文字符
     * 2个小写西里尔字母视为1个中文字符
     * 2个半角标点视为1个中文字符
     * 日语片假名视为1个中文字符
     */
    fun calculateTextLength(text: String): Double {
        var length = 0.0
        for (char in text) {
            if (isChineseCharacter(char) || isKanaCharacter(char)) {
                // 中文字符和日语片假名算1个字符
                length += 1.0
            } else if (isLowercaseLetter(char) || char.isDigit() || isHalfWidthPunctuation(char)) {
                // 小写字母（英语和西里尔）、数字、半角标点算0.5个字符
                length += 0.5
            } else {
                // 其他字符算1个字符
                length += 1.0
            }
        }
        return length
    }
    
    /**
     * 判断字符是否为小写字母（包括英语和西里尔）
     */
    private fun isLowercaseLetter(c: Char): Boolean {
        // 英语小写字母范围：a-z
        // 小写西里尔字母范围：U+0430 到 U+044F
        return c in 'a'..'z' || c in 'а'..'я'
    }
    
    /**
     * 判断字符是否为中文字符
     */
    private fun isChineseCharacter(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT
    }
    
    /**
     * 判断字符是否为日语片假名
     */
    private fun isKanaCharacter(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c)
        return block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS
    }
    
    /**
     * 判断字符是否为半角标点符号
     */
    private fun isHalfWidthPunctuation(char: Char): Boolean {
        return char in setOf(',', '.', '!', '?', ';', ':', '-', '_', '(', ')', '[', ']', '{', '}', '<', '>', '"', '\'', '`', '~', '|', '\\', '/', '%', '^', '&', '*', '+', '=', '|')
    }
    
    /**
     * 判断字符是否为标点符号
     */
    private fun isPunctuation(char: Char): Boolean {
        return char in setOf('，', '。', '！', '？', '；', '：', ',', '.', '!', '?', ';', ':', '、')
    }
    
    /**
     * 截断文本，最长13等价字符，允许最多超出5个字符
     * @param text 原始文本
     * @return 截断后的文本
     */
    private fun truncateText(text: String): String {
        // 最长13等价字符（7+6），超长的直接截断
        val maxEquivalentLength = 13.0
        val maxAllowedLength = 18 // 允许最多超出5个字符
        
        if (text.isEmpty()) {
            return text
        }
        
        if (text.length <= maxAllowedLength) {
            return text
        }
        
        // 计算等价长度，找到截断点
        var currentLength = 0.0
        var truncatePoint = 0
        
        for (i in 0 until text.length) {
            val char = text[i]
            currentLength += if (isChineseCharacter(char) || isKanaCharacter(char)) 1.0 else if (isLowercaseLetter(char) || char.isDigit() || isHalfWidthPunctuation(char)) 0.5 else 1.0
            
            if (currentLength >= maxEquivalentLength) {
                truncatePoint = i + 1
                break
            }
        }
        
        // 如果没有找到截断点（文本等价长度小于maxEquivalentLength），使用maxAllowedLength
        if (truncatePoint == 0) {
            truncatePoint = maxAllowedLength
        }
        
        // 确保截断点不超过最大允许字符数
        truncatePoint = minOf(truncatePoint, maxAllowedLength)
        
        // 确保截断点至少为1
        truncatePoint = maxOf(truncatePoint, 1)
        
        // 尝试在截断点附近寻找偏后的空格或标点符号
        var finalTruncatePoint = truncatePoint
        for (i in truncatePoint until minOf(text.length, truncatePoint + 5)) {
            if (text[i] == ' ' || isPunctuation(text[i])) {
                finalTruncatePoint = i
                break
            }
        }
        
        // 确保finalTruncatePoint至少为1
        finalTruncatePoint = maxOf(finalTruncatePoint, 1)
        
        // 如果没有找到空格或标点，使用原始截断点
        return text.substring(0, finalTruncatePoint)
    }

    /**
     * 拆分歌词文本，考虑字符类型的长度计算
     * @param lyricText 歌词文本
     * @param threshold 拆分阈值（中文字符等价长度）
     * @return Pair(图标文本, 胶囊文本)
     */
    fun splitLyric(lyricText: String, threshold: Int): Pair<String, String> {
        // 截断文本
        val truncatedText = truncateText(lyricText)
        
        if (truncatedText.isEmpty()) {
            return Pair("", "")
        }
        
        val textLength = calculateTextLength(truncatedText)
        if (textLength <= threshold) {
            return Pair("", truncatedText)
        }
        
        // 确保胶囊部分恰好为6等价字符的空间
        val capsuleEquivalentLength = 6.0
        var capsuleSplitPoint = truncatedText.length
        
        // 从后往前计算，找到胶囊部分恰好6等价字符的位置
        var currentLength = 0.0
        for (i in truncatedText.length - 1 downTo 0) {
            val char = truncatedText[i]
            currentLength += if (isChineseCharacter(char) || isKanaCharacter(char)) 1.0 else if (isLowercaseLetter(char) || char.isDigit() || isHalfWidthPunctuation(char)) 0.5 else 1.0
            
            if (currentLength >= capsuleEquivalentLength) {
                capsuleSplitPoint = i
                break
            }
        }
        
        // 确保图标文本长度至少为2个字符，且不超过7等价字符
        val maxIconEquivalentLength = 7.0
        var iconSplitPoint = min(capsuleSplitPoint, truncatedText.length)
        
        // 从前往后计算，找到图标部分不超过7等价字符的位置
        currentLength = 0.0
        for (i in 0 until capsuleSplitPoint) {
            val char = truncatedText[i]
            currentLength += if (isChineseCharacter(char) || isKanaCharacter(char)) 1.0 else if (isLowercaseLetter(char) || char.isDigit() || isHalfWidthPunctuation(char)) 0.5 else 1.0
            
            if (currentLength >= maxIconEquivalentLength) {
                iconSplitPoint = i + 1
                break
            }
        }
        
        // 确保图标文本长度至少为2个字符，且不超过文本长度
        val minSplitPoint = 2
        iconSplitPoint = maxOf(minSplitPoint, iconSplitPoint)
        iconSplitPoint = min(iconSplitPoint, truncatedText.length)
        
        // 确保索引不超出范围
        val safeIconSplitPoint = min(iconSplitPoint, truncatedText.lastIndex.coerceAtLeast(0))
        
        // 计算安全的搜索范围
        val searchStart = minSplitPoint
        val searchEnd = minOf(capsuleSplitPoint, iconSplitPoint + 3, truncatedText.length)
        
        // 从图标拆分点开始，向左寻找最近的空格或标点符号
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
        
        // 如果向左没找到空格或标点，向右寻找
        if (!foundSplitPoint && truncatedText.isNotEmpty() && safeIconSplitPoint < searchEnd) {
            for (i in safeIconSplitPoint until searchEnd) {
                if (truncatedText[i] == ' ' || isPunctuation(truncatedText[i])) {
                    splitPoint = i
                    break
                }
            }
        }
        
        // 确保拆分点不小于最小拆分点，且不超过胶囊拆分点
        val finalSplitPoint = maxOf(minSplitPoint, min(splitPoint, capsuleSplitPoint))
        
        // 执行拆分
        val iconText = truncatedText.take(finalSplitPoint)
        val capsuleText = truncatedText.substring(finalSplitPoint)
        
        return Pair(iconText, capsuleText)
    }
    
    /**
     * 拆分歌词文本，考虑字符类型的长度计算（与splitLyric方法相同，保留为兼容性）
     * @param lyricText 歌词文本
     * @param threshold 拆分阈值（中文字符等价长度）
     * @return Pair(图标文本, 胶囊文本)
     */
    fun splitLyricWithCharacterType(lyricText: String, threshold: Int): Pair<String, String> {
        return splitLyric(lyricText, threshold)
    }
}
