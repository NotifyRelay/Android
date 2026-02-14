package com.xzyht.notifyrelay.feature.notification.superisland.common

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
     * 拆分歌词文本，尽可能平分并在空格位拆分
     * @param lyricText 歌词文本
     * @param threshold 拆分阈值
     * @return Pair(图标文本, 胶囊文本)
     */
    fun splitLyric(lyricText: String, threshold: Int): Pair<String, String> {
        if (lyricText.length <= threshold) {
            return Pair("", lyricText)
        }
        
        // 计算理想的平分点
        val idealSplitPoint = lyricText.length / 2
        
        // 在理想拆分点附近寻找空格，范围为±3个字符
        val searchStart = maxOf(0, idealSplitPoint - 3)
        val searchEnd = minOf(lyricText.length, idealSplitPoint + 3)
        
        // 从理想拆分点开始，向右寻找最近的空格
        var splitPoint = idealSplitPoint
        for (i in idealSplitPoint until searchEnd) {
            if (lyricText[i] == ' ') {
                splitPoint = i
                break
            }
        }
        
        // 如果向右没找到空格，向左寻找
        if (splitPoint == idealSplitPoint) {
            for (i in idealSplitPoint - 1 downTo searchStart) {
                if (lyricText[i] == ' ') {
                    splitPoint = i
                    break
                }
            }
        }
        
        // 确保图标文本长度在2-5个字符之间
        val iconTextLength = splitPoint
        val finalIconTextLength = when {
            iconTextLength < 2 -> 2
            iconTextLength > 5 -> 5
            else -> iconTextLength
        }
        
        // 执行拆分
        val iconText = lyricText.take(finalIconTextLength)
        val capsuleText = lyricText.substring(finalIconTextLength)
        
        return Pair(iconText, capsuleText)
    }
    
    /**
     * 拆分歌词文本，考虑字符类型的长度计算
     * @param lyricText 歌词文本
     * @param threshold 拆分阈值（中文字符等价长度）
     * @return Pair(图标文本, 胶囊文本)
     */
    fun splitLyricWithCharacterType(lyricText: String, threshold: Int): Pair<String, String> {
        val textLength = calculateTextLength(lyricText)
        if (textLength <= threshold) {
            return Pair("", lyricText)
        }
        
        // 计算理想的平分点（按字符数）
        val idealSplitPoint = lyricText.length / 2
        
        // 在理想拆分点附近寻找空格，范围为±3个字符
        val searchStart = maxOf(0, idealSplitPoint - 3)
        val searchEnd = minOf(lyricText.length, idealSplitPoint + 3)
        
        // 从理想拆分点开始，向右寻找最近的空格
        var splitPoint = idealSplitPoint
        for (i in idealSplitPoint until searchEnd) {
            if (lyricText[i] == ' ') {
                splitPoint = i
                break
            }
        }
        
        // 如果向右没找到空格，向左寻找
        if (splitPoint == idealSplitPoint) {
            for (i in idealSplitPoint - 1 downTo searchStart) {
                if (lyricText[i] == ' ') {
                    splitPoint = i
                    break
                }
            }
        }
        
        // 确保图标文本长度在2-5个中文字符等价长度之间
        var actualSplitPoint = splitPoint
        
        // 如果找到了空格，检查空格位置是否在合理范围内
        if (splitPoint > 0) {
            // 计算空格位置的字符等价长度
            var spacePositionLength = 0.0
            for (i in 0 until splitPoint) {
                val char = lyricText[i]
                spacePositionLength += if (char.isLetter()) 0.5 else 1.0
            }
            
            // 如果空格位置的长度在合理范围内，使用空格位置
            if (spacePositionLength >= 2.0 && spacePositionLength <= 5.0) {
                actualSplitPoint = splitPoint
            } else {
                // 否则，计算一个合理的长度位置
                var currentLength = 0.0
                var lengthBasedSplitPoint = 0
                
                for (i in 0 until lyricText.length) {
                    val char = lyricText[i]
                    currentLength += if (char.isLetter()) 0.5 else 1.0
                    
                    if (currentLength >= 2.0) {
                        lengthBasedSplitPoint = i + 1
                        if (currentLength >= 5.0) {
                            break
                        }
                    }
                }
                actualSplitPoint = lengthBasedSplitPoint
            }
        } else {
            // 没有找到空格，计算一个合理的长度位置
            var currentLength = 0.0
            var lengthBasedSplitPoint = 0
            
            for (i in 0 until lyricText.length) {
                val char = lyricText[i]
                currentLength += if (char.isLetter()) 0.5 else 1.0
                
                if (currentLength >= 2.0) {
                    lengthBasedSplitPoint = i + 1
                    if (currentLength >= 5.0) {
                        break
                    }
                }
            }
            actualSplitPoint = lengthBasedSplitPoint
        }
        
        // 执行拆分
        val iconText = lyricText.take(actualSplitPoint)
        val capsuleText = lyricText.substring(actualSplitPoint)
        
        return Pair(iconText, capsuleText)
    }
}