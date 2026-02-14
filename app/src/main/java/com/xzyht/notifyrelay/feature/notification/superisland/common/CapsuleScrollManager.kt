package com.xzyht.notifyrelay.feature.notification.superisland.common

import android.os.Handler
import android.os.Looper
import notifyrelay.base.util.Logger

/**
 * 胶囊文本滚动管理器，用于处理超长文本的滚动显示
 */
object CapsuleScrollManager {
    private const val TAG = "CapsuleScrollManager"
    
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
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 基于视觉权重的滚动（CJK=2，西文=1）
    private const val maxDisplayWeight = 18  // 视觉容量：约11个CJK字符或约22个西文字符
    private const val compensationThreshold = 8  // 如果剩余权重小于此值则停止滚动（保持胶囊稳定）
    
    // 时间常量
    private const val initialPauseDuration = 1000L  // 1秒用于阅读开头
    private const val finalPauseDuration = 500L     // 下一句文本前的0.5秒
    private const val baseFocusDelay = 500L         // 每次切换的眼睛重聚焦时间
    private const val staticTimeReserve = 1500L     // 初始+最终暂停预留时间
    private const val SCROLL_STEP_DELAY = 1800L     // 滚动步长延迟
    private const val minScrollDelay = 500L         // 最小滚动延迟
    private const val maxScrollDelay = 5000L         // 最大滚动延迟
    private const val minCharDuration = 50L         // 最小字符持续时间
    
    // 自适应滚动速度跟踪
    private data class AdaptiveData(
        var lastTextChangeTime: Long = 0,
        var lastTextLength: Int = 0,
        val textDurations: MutableList<Long> = mutableListOf(),
        val maxHistory: Int = 5
    )
    
    private val adaptiveDataMap = mutableMapOf<String, AdaptiveData>()
    
    /**
     * 计算字符的视觉权重（CJK=2，西文=1）
     */
    private fun charWeight(c: Char): Int {
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
     * 计算智能移动权重（CJK: 2-3字符=4-6权重，西文: 3-4字符=3-4权重）
     */
    private fun calculateSmartShiftWeight(text: String, currentOffset: Int): Int {
        // 提取从当前偏移量开始的片段
        val segment = extractByWeight(text, currentOffset, 10)  // 向前查看10个权重
        if (segment.isEmpty()) return 4  // 默认值
        
        // 检测主要是CJK还是西文
        val cjkCount = segment.count { charWeight(it) == 2 }
        val totalChars = segment.length
        
        val isCJK = cjkCount > totalChars / 2
        
        return if (isCJK) {
            // CJK: 移动2-3个字符（4-6权重），优先2个字符
            if (segment.length >= 2) {
                charWeight(segment[0]) + charWeight(segment[1])  // 2个字符
            } else {
                4  // 回退值
            }
        } else {
            // 西文: 移动3-4个字符（3-4权重）
            // 尝试找到单词边界（空格）
            val spaceIndex = segment.indexOf(' ', 2)  // 查找2个字符后的空格
            if (spaceIndex in 2..4) {
                // 移动到空格
                calculateWeight(segment.take(spaceIndex + 1))
            } else if (segment.length >= 3) {
                // 移动3个字符
                calculateWeight(segment.take(3))
            } else {
                3  // 回退值
            }
        }
    }
    
    /**
     * 记录文本变化，用于自适应滚动速度
     */
    fun recordTextChange(key: String, newText: String) {
        val now = System.currentTimeMillis()
        val adaptiveData = adaptiveDataMap.getOrPut(key) { AdaptiveData() }
        
        // 跳过第一句文本（没有之前的时间记录）
        if (adaptiveData.lastTextChangeTime == 0L) {
            adaptiveData.lastTextChangeTime = now
            adaptiveData.lastTextLength = newText.length
            return
        }
        
        val duration = now - adaptiveData.lastTextChangeTime
        val avgCharDuration = if (adaptiveData.lastTextLength > 0) duration / adaptiveData.lastTextLength else 0
        
        // 过滤噪音：如果更新太快则忽略（每字符<50ms）
        if (avgCharDuration < minCharDuration) {
            Logger.d(TAG, "忽略快速更新: ${avgCharDuration}ms/字符")
            return
        }
        
        // 过滤暂停：如果太慢则忽略（总时长>30s）
        if (duration > 30000) {
            Logger.d(TAG, "忽略长暂停: ${duration}ms")
            adaptiveData.lastTextChangeTime = now
            adaptiveData.lastTextLength = newText.length
            return
        }
        
        // 添加到历史记录（滑动窗口）
        adaptiveData.textDurations.add(duration)
        if (adaptiveData.textDurations.size > adaptiveData.maxHistory) {
            adaptiveData.textDurations.removeAt(0)
        }
        
        // 更新状态
        adaptiveData.lastTextChangeTime = now
        adaptiveData.lastTextLength = newText.length
        
        // 重新计算自适应延迟
        calculateAdaptiveDelay(key)
    }
    
    /**
     * 计算自适应滚动延迟
     */
    private fun calculateAdaptiveDelay(key: String) {
        val adaptiveData = adaptiveDataMap[key] ?: return
        val scrollData = scrollDataMap[key] ?: return
        
        val avgDuration = if (adaptiveData.textDurations.isEmpty()) {
            scrollData.adaptiveDelay = SCROLL_STEP_DELAY
            return
        } else {
            adaptiveData.textDurations.average().toLong()
        }
        
        // 计算最近文本的总视觉权重
        val avgTextWeight = calculateWeight(scrollData.lastText)
        
        if (avgTextWeight == 0 || avgDuration < staticTimeReserve) {
            scrollData.adaptiveDelay = SCROLL_STEP_DELAY
            return
        }
        
        // 估计需要的滚动步数
        val estimatedSteps = maxOf(1, (avgTextWeight / 5))  // 每次移动约5个权重
        
        // T_per_unit = (T_total - T_static - N*T_base) / L_total
        val availableTime = avgDuration - staticTimeReserve - (estimatedSteps * baseFocusDelay)
        val timePerUnit = if (availableTime > 0 && avgTextWeight > 0) {
            availableTime / avgTextWeight
        } else {
            100L  // 回退值
        }
        
        // T_wait = T_base + (T_per_unit × W_shift)
        // 假设平均移动权重约为5（2-3个CJK字符或3-4个西文字符）
        val avgShiftWeight = 5
        val calculatedDelay = baseFocusDelay + (timePerUnit * avgShiftWeight)
        
        // 限制在合理范围内
        scrollData.adaptiveDelay = calculatedDelay.coerceIn(minScrollDelay, maxScrollDelay)
        
        Logger.d(TAG, "自适应滚动: ${scrollData.adaptiveDelay}ms (平均权重: $avgTextWeight, 每单位时间: ${timePerUnit}ms)")
    }
    
    /**
     * 获取当前应该显示的文本片段
     */
    fun getCurrentDisplayText(key: String, text: String): String {
        val scrollData = scrollDataMap.getOrPut(key) { ScrollData() }
        
        // 如果文本更改，重置滚动偏移量
        if (text != scrollData.lastText) {
            scrollData.lastText = text
            scrollData.scrollOffset = 0
            scrollData.scrollState = ScrollState.SCROLLING
            scrollData.initialPauseStartTime = System.currentTimeMillis()
        }
        
        val totalWeight = calculateWeight(text)
        
        // 短文本：无需滚动
        if (totalWeight <= maxDisplayWeight) {
            scrollData.scrollState = ScrollState.DONE
            return text
        }
        
        // 滚动时序的状态机
        return when (scrollData.scrollState) {
            ScrollState.SCROLLING -> {
                // 计算剩余内容
                val remainingWeight = totalWeight - scrollData.scrollOffset
                
                // 补偿算法：如果剩余权重较小则停止滚动以保持胶囊稳定
                if (remainingWeight <= compensationThreshold) {
                    // 显示所有剩余内容（即使>最大显示权重）
                    scrollData.scrollState = ScrollState.FINAL_PAUSE
                    scrollData.initialPauseStartTime = System.currentTimeMillis()
                    extractByWeight(text, scrollData.scrollOffset, remainingWeight)
                } else if (remainingWeight <= maxDisplayWeight) {
                    // 最后完整片段：切换到FINAL_PAUSE
                    scrollData.scrollState = ScrollState.FINAL_PAUSE
                    scrollData.initialPauseStartTime = System.currentTimeMillis()
                    extractByWeight(text, scrollData.scrollOffset, maxDisplayWeight)
                } else {
                    // 主动滚动
                    val displayText = extractByWeight(text, scrollData.scrollOffset, maxDisplayWeight)
                    
                    // 按智能步长增加滚动偏移量（2-3个CJK或3-4个西文字符）
                    scrollData.scrollOffset += calculateSmartShiftWeight(text, scrollData.scrollOffset)
                    
                    displayText
                }
            }
            
            ScrollState.FINAL_PAUSE -> {
                // 显示最终片段（由于补偿可能>最大显示权重）
                val remainingWeight = totalWeight - scrollData.scrollOffset
                val displayText = extractByWeight(text, scrollData.scrollOffset, maxOf(remainingWeight, maxDisplayWeight))
                
                val pauseElapsed = System.currentTimeMillis() - scrollData.initialPauseStartTime
                if (pauseElapsed >= finalPauseDuration) {
                    scrollData.scrollState = ScrollState.DONE
                }
                
                displayText
            }
            
            ScrollState.DONE -> {
                // 保持显示最终片段
                val remainingWeight = totalWeight - scrollData.scrollOffset
                extractByWeight(text, scrollData.scrollOffset, maxOf(remainingWeight, maxDisplayWeight))
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