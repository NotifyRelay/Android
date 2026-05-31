package github.xzynine.superislandui.common

/**
 * 胶囊文本滚动管理器，用于处理超长文本的滚动显示
 */
object CapsuleScrollManager {

    // 滚动状态机
    private enum class ScrollState {
        SCROLLING,      // 主动滚动
        FINAL_PAUSE,    // 在下一句文本前显示结尾
        DONE
    }
    
    // 滚动状态数据类
    private data class ScrollData(
        var scrollState: ScrollState = ScrollState.SCROLLING,
        var scrollOffset: Int = 0,
        var initialPauseStartTime: Long = 0,
        var lastUpdateTime: Long = 0,
        var lastText: String = "",
        var adaptiveDelay: Long = SCROLL_STEP_DELAY
    )
    
    // 存储不同通知的滚动状态
    private val scrollDataMap = mutableMapOf<String, ScrollData>()

    // 基于视觉权重的滚动（CJK=2，西文=1）
    const val DEFAULT_MAX_DISPLAY_WEIGHT = 17  // 默认视觉容量：约10个CJK字符或约20个西文字符
    private const val compensationThreshold = 20  // 如果剩余权重小于此值则停止滚动（保持胶囊稳定）

    private const val initialPauseDuration = 400L  // 新歌词开始前的初始暂停
    private const val finalPauseDuration = 300L    // 滚动到末尾后的最终暂停
    private const val SCROLL_STEP_DELAY = 1200L    // 滚动步长延迟

    // 自适应滚动速度跟踪
    private data class AdaptiveData(
        var lastTextChangeTime: Long = 0,
        var lastTextLength: Int = 0,
        val textDurations: MutableList<Long> = mutableListOf(),
        val maxHistory: Int = 5
    )
    
    private val adaptiveDataMap = mutableMapOf<String, AdaptiveData>()
    
    /**
     * 计算字符的视觉权重（CJK=2，西文=1，空白=0）
     */
    private fun charWeight(c: Char): Int {
        if (c.isWhitespace()) return 0
        return when (Character.UnicodeBlock.of(c)) {
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
            Character.UnicodeBlock.HIRAGANA,
            Character.UnicodeBlock.KATAKANA,
            Character.UnicodeBlock.HANGUL_SYLLABLES -> 2  // CJK字符
            else -> 1  // 西文字符、数字、符号
        }
    }
    
    /**
     * 计算字符串的总视觉权重
     */
    fun calculateWeight(text: String): Int {
        return text.sumOf { charWeight(it) }
    }
    
    /**
     * 按视觉权重提取子串（而非字符计数）
     */
    private fun extractByWeight(text: String, startWeight: Int, maxWeight: Int): String {
        var currentWeight = 0
        var startIndex = 0
        var endIndex = 0
        
        // 查找起始位置
        for (i in text.indices) {
            if (currentWeight >= startWeight) {
                startIndex = i
                break
            }
            currentWeight += charWeight(text[i])
        }
        
        // 查找结束位置
        currentWeight = 0
        for (i in startIndex until text.length) {
            currentWeight += charWeight(text[i])
            if (currentWeight > maxWeight) {
                break
            }
            endIndex = i + 1
        }
        
        return if (endIndex > startIndex) text.substring(startIndex, endIndex) else ""
    }
    
    /**
     * 计算智能移动权重
     * CJK: 每次移动1个字符（2权重），日语更平滑
     * 西文: 每次移动2权重
     */
    private fun calculateSmartShiftWeight(text: String, currentOffset: Int): Int {
        val segment = extractByWeight(text, currentOffset, 10)
        if (segment.isEmpty()) return 2
        
        val nonWhitespaceChars = segment.count { !it.isWhitespace() }
        val cjkCount = segment.count { !it.isWhitespace() && charWeight(it) == 2 }
        val isCJK = nonWhitespaceChars > 0 && cjkCount > nonWhitespaceChars / 2
        
        return if (isCJK) {
            if (segment.isNotEmpty()) charWeight(segment[0]) else 2
        } else {
            2
        }
    }

    /**
     * 获取当前应该显示的文本片段
     * @param key 滚动状态键
     * @param text 完整文本
     * @param maxWeight 单段最大显示权重（CJK=2，西文=1），默认使用 DEFAULT_MAX_DISPLAY_WEIGHT
     */
    fun getCurrentDisplayText(key: String, text: String, maxWeight: Int = DEFAULT_MAX_DISPLAY_WEIGHT): String {
        val scrollData = scrollDataMap.getOrPut(key) { ScrollData() }
        
        // 如果文本更改，重置滚动偏移量
        if (text != scrollData.lastText) {
            scrollData.lastText = text
            scrollData.scrollOffset = 0
            scrollData.scrollState = ScrollState.SCROLLING
            scrollData.initialPauseStartTime = System.currentTimeMillis()
        }
        
        val totalWeight = calculateWeight(text)
        
        // 根据当前maxWeight等比缩放补偿阈值
        val scaledCompensationThreshold = compensationThreshold * maxWeight / DEFAULT_MAX_DISPLAY_WEIGHT
        
        // 短文本：无需滚动
        if (totalWeight <= maxWeight) {
            scrollData.scrollState = ScrollState.DONE
            return text
        }
        
        // 滚动时序的状态机
        return when (scrollData.scrollState) {
            ScrollState.SCROLLING -> {
                // 初始暂停：让第一段内容显示足够长时间再开始滚动
                if (scrollData.scrollOffset == 0) {
                    val initialPauseElapsed = System.currentTimeMillis() - scrollData.initialPauseStartTime
                    if (initialPauseElapsed < initialPauseDuration) {
                        return extractByWeight(text, 0, maxWeight)
                    }
                }
                // 计算剩余内容
                val remainingWeight = totalWeight - scrollData.scrollOffset
                
                // 补偿算法：如果剩余权重较小则停止滚动以保持胶囊稳定
                if (remainingWeight <= scaledCompensationThreshold) {
                    // 显示所有剩余内容（即使>最大显示权重）
                    scrollData.scrollState = ScrollState.FINAL_PAUSE
                    scrollData.initialPauseStartTime = System.currentTimeMillis()
                    extractByWeight(text, scrollData.scrollOffset, remainingWeight)
                } else if (remainingWeight <= maxWeight) {
                    // 最后完整片段：切换到FINAL_PAUSE
                    scrollData.scrollState = ScrollState.FINAL_PAUSE
                    scrollData.initialPauseStartTime = System.currentTimeMillis()
                    extractByWeight(text, scrollData.scrollOffset, maxWeight)
                } else {
                    // 主动滚动
                    val displayText = extractByWeight(text, scrollData.scrollOffset, maxWeight)
                    
                    // 按智能步长增加滚动偏移量（CJK:1字符=2权重，西文:2权重）
                    scrollData.scrollOffset += calculateSmartShiftWeight(text, scrollData.scrollOffset)
                    
                    displayText
                }
            }
            
            ScrollState.FINAL_PAUSE -> {
                // 显示最终片段（由于补偿可能>最大显示权重）
                val remainingWeight = totalWeight - scrollData.scrollOffset
                val displayText = extractByWeight(text, scrollData.scrollOffset, maxOf(remainingWeight, maxWeight))
                
                val pauseElapsed = System.currentTimeMillis() - scrollData.initialPauseStartTime
                if (pauseElapsed >= finalPauseDuration) {
                    scrollData.scrollState = ScrollState.DONE
                }
                
                displayText
            }
            
            ScrollState.DONE -> {
                // 保持显示最终片段
                val remainingWeight = totalWeight - scrollData.scrollOffset
                extractByWeight(text, scrollData.scrollOffset, maxOf(remainingWeight, maxWeight))
            }
        }
    }
    
    /**
     * 检查是否需要更新通知
     */
    fun shouldUpdateNotification(key: String): Boolean {
        val scrollData = scrollDataMap[key] ?: return false
        val now = System.currentTimeMillis()
        
        // 节流：50ms限制
        if (now - scrollData.lastUpdateTime < 50) return false
        
        scrollData.lastUpdateTime = now
        return true
    }
    
    /**
     * 重置滚动状态
     */
    fun resetScrollState(key: String) {
        scrollDataMap.remove(key)
        adaptiveDataMap.remove(key)
    }
    
    /**
     * 获取滚动延迟
     */
    fun getScrollDelay(key: String): Long {
        val scrollData = scrollDataMap[key] ?: return SCROLL_STEP_DELAY
        return scrollData.adaptiveDelay
    }
    
    /**
     * 清理所有滚动状态
     */
    fun clearAll() {
        scrollDataMap.clear()
        adaptiveDataMap.clear()
    }
}
