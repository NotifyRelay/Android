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
            if (char.isLetter()) {
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
        var currentLength = 0.0
        var finalSplitPoint = 0
        
        for (i in 0 until lyricText.length) {
            val char = lyricText[i]
            currentLength += if (char.isLetter()) 0.5 else 1.0
            
            if (currentLength >= 2.0) {
                finalSplitPoint = i + 1
                if (currentLength >= 5.0) {
                    break
                }
            }
        }
        
        // 使用找到的空格位置或计算的长度位置
        val actualSplitPoint = if (splitPoint > 0) splitPoint else finalSplitPoint
        
        // 执行拆分
        val iconText = lyricText.take(actualSplitPoint)
        val capsuleText = lyricText.substring(actualSplitPoint)
        
        return Pair(iconText, capsuleText)
    }
}