package com.xzyht.notifyrelay.feature.notification.superisland.common

import kotlin.math.min

/**
 * 文本拆分工具类，用于处理歌词等文本的拆分
 */
object TextSplitter {
    
    /**
     * 计算文本的中文字符等价长度
     * 2个英语字符视为1个中文字符
     */
    fun calculateTextLength(text: String): Double {
        var length = 0.0
        for (char in text) {
            if (isChineseCharacter(char)) {
                // 中文字符算1个字符
                length += 1.0
            } else if (char.isLetter()) {
                // 英语字母算0.5个字符
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
     * 截断文本，最长13等价字符，允许最多超出5个字符
     * @param text 原始文本
     * @param maxEquivalentLength 最大等价字符长度
     * @param maxAllowedLength 最大允许字符数
     * @return 截断后的文本
     */
    private fun truncateText(text: String, maxEquivalentLength: Double, maxAllowedLength: Int): String {
        if (text.length <= maxAllowedLength) {
            return text
        }
        
        // 计算等价长度，找到截断点
        var currentLength = 0.0
        var truncatePoint = 0
        
        for (i in 0 until text.length) {
            val char = text[i]
            currentLength += if (isChineseCharacter(char)) 1.0 else if (char.isLetter()) 0.5 else 1.0
            
            if (currentLength >= maxEquivalentLength) {
                truncatePoint = i + 1
                break
            }
        }
        
        // 确保截断点不超过最大允许长度
        truncatePoint = minOf(truncatePoint, maxAllowedLength)
        
        // 尝试在截断点附近寻找偏后的空格
        var finalTruncatePoint = truncatePoint
        for (i in truncatePoint until minOf(text.length, truncatePoint + 5)) {
            if (text[i] == ' ') {
                finalTruncatePoint = i
                break
            }
        }
        
        // 如果没有找到空格，使用原始截断点
        return text.substring(0, finalTruncatePoint)
    }
    
    /**
     * 拆分歌词文本，尽可能平分并在空格位拆分
     * @param lyricText 歌词文本
     * @param threshold 拆分阈值
     * @return Pair(图标文本, 胶囊文本)
     */
    fun splitLyric(lyricText: String, threshold: Int): Pair<String, String> {
        // 最长13等价字符，超长的直接截断
        val maxEquivalentLength = 13.0
        val maxAllowedLength = 17 // 允许最多超出5个字符
        
        // 截断文本
        val truncatedText = truncateText(lyricText, maxEquivalentLength, maxAllowedLength)
        
        if (truncatedText.length <= threshold) {
            return Pair("", truncatedText)
        }
        
        // 确保胶囊部分至少有6等价字符的空间
        val minCapsuleEquivalentLength = 6.0
        var capsuleSplitPoint = truncatedText.length
        
        // 从后往前计算，找到胶囊部分至少6等价字符的位置
        var currentLength = 0.0
        for (i in truncatedText.length - 1 downTo 0) {
            val char = truncatedText[i]
            currentLength += if (isChineseCharacter(char)) 1.0 else if (char.isLetter()) 0.5 else 1.0
            
            if (currentLength >= minCapsuleEquivalentLength) {
                capsuleSplitPoint = i
                break
            }
        }
        
        // 计算理想的平分点
        val idealSplitPoint = min(capsuleSplitPoint, truncatedText.length / 2)
        
        // 确保胶囊文本长度不超过7个字符
        val maxCapsuleLength = 7
        val minSplitPoint = maxOf(2, truncatedText.length - maxCapsuleLength)
        
        // 在理想拆分点附近寻找空格，范围为从minSplitPoint到理想拆分点+3
        val searchStart = minSplitPoint
        val searchEnd = minOf(capsuleSplitPoint, idealSplitPoint + 3)
        
        // 从理想拆分点开始，向右寻找最近的空格
        var splitPoint = idealSplitPoint
        var foundSpace = false
        for (i in idealSplitPoint until searchEnd) {
            if (truncatedText[i] == ' ') {
                splitPoint = i
                foundSpace = true
                break
            }
        }
        
        // 如果向右没找到空格，向左寻找
        if (!foundSpace) {
            for (i in idealSplitPoint - 1 downTo searchStart) {
                if (truncatedText[i] == ' ') {
                    splitPoint = i
                    foundSpace = true
                    break
                }
            }
        }
        
        // 如果没有找到空格，使用最小拆分点
        var finalSplitPoint = if (foundSpace) splitPoint else minSplitPoint
        
        // 确保拆分点不小于最小拆分点，且不超过胶囊拆分点
        finalSplitPoint = maxOf(minSplitPoint, min(finalSplitPoint, capsuleSplitPoint))
        
        // 执行拆分
        val iconText = truncatedText.take(finalSplitPoint)
        val capsuleText = truncatedText.substring(finalSplitPoint)
        
        return Pair(iconText, capsuleText)
    }
    
    /**
     * 拆分歌词文本，考虑字符类型的长度计算
     * @param lyricText 歌词文本
     * @param threshold 拆分阈值（中文字符等价长度）
     * @return Pair(图标文本, 胶囊文本)
     */
    fun splitLyricWithCharacterType(lyricText: String, threshold: Int): Pair<String, String> {
        // 最长13等价字符，超长的直接截断
        val maxEquivalentLength = 13.0
        val maxAllowedLength = 17 // 允许最多超出5个字符
        
        // 截断文本
        val truncatedText = truncateText(lyricText, maxEquivalentLength, maxAllowedLength)
        
        val textLength = calculateTextLength(truncatedText)
        if (textLength <= threshold) {
            return Pair("", truncatedText)
        }
        
        // 确保胶囊部分至少有6等价字符的空间
        val minCapsuleEquivalentLength = 6.0
        var capsuleSplitPoint = truncatedText.length
        
        // 从后往前计算，找到胶囊部分至少6等价字符的位置
        var currentLength = 0.0
        for (i in truncatedText.length - 1 downTo 0) {
            val char = truncatedText[i]
            currentLength += if (isChineseCharacter(char)) 1.0 else if (char.isLetter()) 0.5 else 1.0
            
            if (currentLength >= minCapsuleEquivalentLength) {
                capsuleSplitPoint = i
                break
            }
        }
        
        // 计算理想的平分点（按字符数）
        val idealSplitPoint = min(capsuleSplitPoint, truncatedText.length / 2)
        
        // 确保胶囊文本长度不超过7个字符
        val maxCapsuleLength = 7
        val minSplitPoint = maxOf(2, truncatedText.length - maxCapsuleLength)
        
        // 在理想拆分点附近寻找空格，范围为从minSplitPoint到理想拆分点+3
        val searchStart = minSplitPoint
        val searchEnd = minOf(capsuleSplitPoint, idealSplitPoint + 3)
        
        // 从理想拆分点开始，向右寻找最近的空格
        var splitPoint = idealSplitPoint
        var foundSpace = false
        for (i in idealSplitPoint until searchEnd) {
            if (truncatedText[i] == ' ') {
                splitPoint = i
                foundSpace = true
                break
            }
        }
        
        // 如果向右没找到空格，向左寻找
        if (!foundSpace) {
            for (i in idealSplitPoint - 1 downTo searchStart) {
                if (truncatedText[i] == ' ') {
                    splitPoint = i
                    foundSpace = true
                    break
                }
            }
        }
        
        // 确保图标文本长度至少为2个中文字符等价长度
        var actualSplitPoint = splitPoint
        
        // 如果找到了空格，使用空格位置
        if (foundSpace) {
            actualSplitPoint = splitPoint
        } else {
            // 没有找到空格，计算一个合理的长度位置
            var currentLength = 0.0
            var lengthBasedSplitPoint = 0
            
            // 对于纯中文文本，使用更合理的拆分比例
            val isChineseOnly = truncatedText.all { isChineseCharacter(it) }
            if (isChineseOnly) {
                // 对于纯中文文本，使用接近50%的拆分比例
                lengthBasedSplitPoint = truncatedText.length / 2
            } else {
                // 对于混合文本，使用原来的长度计算逻辑
                for (i in 0 until truncatedText.length) {
                    val char = truncatedText[i]
                    currentLength += if (isChineseCharacter(char)) 1.0 else if (char.isLetter()) 0.5 else 1.0
                    
                    if (currentLength >= 2.0) {
                        lengthBasedSplitPoint = i + 1
                    }
                }
            }
            actualSplitPoint = lengthBasedSplitPoint
        }
        
        // 确保拆分点不小于最小拆分点，且不超过胶囊拆分点
        var finalSplitPoint = maxOf(minSplitPoint, min(actualSplitPoint, capsuleSplitPoint))
        
        // 执行拆分
        val iconText = truncatedText.take(finalSplitPoint)
        val capsuleText = truncatedText.substring(finalSplitPoint)
        
        return Pair(iconText, capsuleText)
    }
}