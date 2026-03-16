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
     * 2个空格视为1个中文字符
     * 日语片假名视为1个中文字符
     */
    fun calculateTextLength(text: String): Double {
        var length = 0.0
        for (char in text) {
            if (isChineseCharacter(char) || isKanaCharacter(char)) {
                // 中文字符和日语片假名算1个字符
                length += 1.0
            } else if (char.isLetter() || char.isDigit() || isHalfWidthPunctuation(char) || char == ' ') {
                // 英语字母、数字、半角标点和空格算0.5个字符
                length += 0.5
            } else {
                // 其他字符算1个字符
                length += 1.0
            }
        }
        return length
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
     * 截断文本，最长21等价字符（14+7），允许最多超出5个字符
     * @param text 原始文本
     * @param maxEquivalentLength 最大等价字符长度
     * @param maxAllowedLength 最大允许字符数
     * @return 截断后的文本
     */
    private fun truncateText(text: String, maxEquivalentLength: Double, maxAllowedLength: Int): String {
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
            currentLength += if (isChineseCharacter(char) || isKanaCharacter(char)) 1.0 else if (char.isLetter() || char.isDigit() || isHalfWidthPunctuation(char) || char == ' ') 0.5 else 1.0
            
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
        // 最长13等价字符（7+6），超长的直接截断
        val maxEquivalentLength = 13.0
        val maxAllowedLength = 18 // 允许最多超出5个字符
        
        // 截断文本
        val truncatedText = truncateText(lyricText, maxEquivalentLength, maxAllowedLength)
        
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
            currentLength += if (isChineseCharacter(char) || isKanaCharacter(char)) 1.0 else if (char.isLetter() || char.isDigit() || isHalfWidthPunctuation(char) || char == ' ') 0.5 else 1.0
            
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
            currentLength += if (isChineseCharacter(char) || isKanaCharacter(char)) 1.0 else if (char.isLetter() || char.isDigit() || isHalfWidthPunctuation(char) || char == ' ') 0.5 else 1.0
            
            if (currentLength >= maxIconEquivalentLength) {
                iconSplitPoint = i + 1
                break
            }
        }
        
        // 确保图标文本长度至少为2个字符
        val minSplitPoint = maxOf(2, 0)
        iconSplitPoint = maxOf(minSplitPoint, iconSplitPoint)
        
        // 在图标拆分点附近寻找空格或标点符号，范围为从minSplitPoint到图标拆分点+3
        val searchStart = minSplitPoint
        val searchEnd = minOf(capsuleSplitPoint, iconSplitPoint + 3)
        
        // 从图标拆分点开始，向左寻找最近的空格或标点符号
        var splitPoint = iconSplitPoint
        var foundSplitPoint = false
        for (i in iconSplitPoint downTo searchStart) {
            if (truncatedText[i] == ' ' || isPunctuation(truncatedText[i])) {
                splitPoint = i
                foundSplitPoint = true
                break
            }
        }
        
        // 如果向左没找到空格或标点，向右寻找
        if (!foundSplitPoint) {
            for (i in iconSplitPoint until searchEnd) {
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
