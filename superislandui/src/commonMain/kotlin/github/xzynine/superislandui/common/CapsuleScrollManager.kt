package github.xzynine.superislandui.common

import notifyrelay.shared.util.nowMillis

/**
 * 胶囊文本滚动管理器，用于处理超长文本的滚动显示
 */
object CapsuleScrollManager {
    // 滚动状态机
    private enum class ScrollState {
        SCROLLING, // 主动滚动
        FINAL_PAUSE, // 在下一句文本前显示结尾
        DONE,
    }

    // 滚动状态数据类
    private data class ScrollData(
        var scrollState: ScrollState = ScrollState.SCROLLING,
        var scrollOffset: Int = 0,
        var initialPauseStartTime: Long = 0,
        var lastUpdateTime: Long = 0,
        var lastText: String = "",
        var adaptiveDelay: Long = SCROLL_STEP_DELAY,
    )

    // 存储不同通知的滚动状态
    private val scrollDataMap = mutableMapOf<String, ScrollData>()

    // 基于视觉权重的滚动（CJK=2，西文=1）
    private const val MAX_DISPLAY_WEIGHT = 17 // 视觉容量：约10个CJK字符或约20个西文字符
    private const val COMPENSATION_THRESHOLD = 7 // 如果剩余权重小于此值则停止滚动（保持胶囊稳定）

    private const val FINAL_PAUSE_DURATION = 500L // 下一句文本前的0.5秒
    private const val SCROLL_STEP_DELAY = 1800L // 滚动步长延迟

    // 自适应滚动速度跟踪
    private data class AdaptiveData(
        var lastTextChangeTime: Long = 0,
        var lastTextLength: Int = 0,
        val textDurations: MutableList<Long> = mutableListOf(),
        val maxHistory: Int = 5,
    )

    private val adaptiveDataMap = mutableMapOf<String, AdaptiveData>()

    /**
     * 计算字符的视觉权重（CJK=2，西文=1）
     */
    fun charWeight(c: Char): Int = if (isCjk(c)) 2 else 1

    /**
     * 是否为 CJK 字符。
     *
     * 区间取自 java.lang.Character.UnicodeBlock 的区块定义，逐字等价：
     * 区块本身即连续码点区间，且 char 维度下 UTF-16 代理区不属于上述任一区块。
     */
    private fun isCjk(c: Char): Boolean {
        val code = c.code
        return CJK_CODE_RANGES.any { code in it }
    }

    /** CJK 码点区间，取自 java.lang.Character.UnicodeBlock 的区块定义。 */
    private val CJK_CODE_RANGES =
        listOf(
            0x4E00..0x9FFF, // CJK 统一表意文字
            0x3400..0x4DBF, // CJK 统一表意文字扩展 A
            0x20000..0x2A6DF, // CJK 统一表意文字扩展 B
            0xF900..0xFAFF, // CJK 兼容表意文字
            0x3040..0x309F, // 平假名
            0x30A0..0x30FF, // 片假名
            0xAC00..0xD7AF, // 谚文音节
        )

    /**
     * 计算字符串的总视觉权重
     */
    fun calculateWeight(text: String): Int = text.sumOf { charWeight(it) }

    /**
     * 按视觉权重提取子串（而非字符计数）
     */
    private fun extractByWeight(
        text: String,
        startWeight: Int,
        maxWeight: Int,
    ): String {
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
    private fun calculateSmartShiftWeight(
        text: String,
        currentOffset: Int,
    ): Int {
        // 提取从当前偏移量开始的片段
        val segment = extractByWeight(text, currentOffset, 10) // 向前查看10个权重
        if (segment.isEmpty()) return 4 // 默认值

        // 检测主要是CJK还是西文
        val cjkCount = segment.count { charWeight(it) == 2 }
        val totalChars = segment.length

        val isCJK = cjkCount > totalChars / 2

        return if (isCJK) {
            // CJK: 移动2-3个字符（4-6权重），优先2个字符
            if (segment.length >= 2) {
                charWeight(segment[0]) + charWeight(segment[1]) // 2个字符
            } else {
                4 // 回退值
            }
        } else {
            // 西文: 移动3-4个字符（3-4权重）
            // 尝试找到单词边界（空格）
            val spaceIndex = segment.indexOf(' ', 2) // 查找2个字符后的空格
            if (spaceIndex in 2..4) {
                // 移动到空格
                calculateWeight(segment.take(spaceIndex + 1))
            } else if (segment.length >= 3) {
                // 移动3个字符
                calculateWeight(segment.take(3))
            } else {
                3 // 回退值
            }
        }
    }

    /**
     * 获取当前应该显示的文本片段
     */
    fun getCurrentDisplayText(
        key: String,
        text: String,
    ): String {
        val scrollData = scrollDataMap.getOrPut(key) { ScrollData() }

        // 如果文本更改，重置滚动偏移量
        if (text != scrollData.lastText) {
            scrollData.lastText = text
            scrollData.scrollOffset = 0
            scrollData.scrollState = ScrollState.SCROLLING
            scrollData.initialPauseStartTime = nowMillis()
        }

        val totalWeight = calculateWeight(text)

        // 短文本：无需滚动
        if (totalWeight <= MAX_DISPLAY_WEIGHT) {
            scrollData.scrollState = ScrollState.DONE
            return text
        }

        // 滚动时序的状态机
        return when (scrollData.scrollState) {
            ScrollState.SCROLLING -> {
                // 计算剩余内容
                val remainingWeight = totalWeight - scrollData.scrollOffset

                // 补偿算法：如果剩余权重较小则停止滚动以保持胶囊稳定
                if (remainingWeight <= COMPENSATION_THRESHOLD) {
                    // 显示所有剩余内容（即使>最大显示权重）
                    scrollData.scrollState = ScrollState.FINAL_PAUSE
                    scrollData.initialPauseStartTime = nowMillis()
                    extractByWeight(text, scrollData.scrollOffset, remainingWeight)
                } else if (remainingWeight <= MAX_DISPLAY_WEIGHT) {
                    // 最后完整片段：切换到FINAL_PAUSE
                    scrollData.scrollState = ScrollState.FINAL_PAUSE
                    scrollData.initialPauseStartTime = nowMillis()
                    extractByWeight(text, scrollData.scrollOffset, MAX_DISPLAY_WEIGHT)
                } else {
                    // 主动滚动
                    val displayText = extractByWeight(text, scrollData.scrollOffset, MAX_DISPLAY_WEIGHT)

                    // 按智能步长增加滚动偏移量（2-3个CJK或3-4个西文字符）
                    scrollData.scrollOffset += calculateSmartShiftWeight(text, scrollData.scrollOffset)

                    displayText
                }
            }

            ScrollState.FINAL_PAUSE -> {
                // 显示最终片段（由于补偿可能>最大显示权重）
                val remainingWeight = totalWeight - scrollData.scrollOffset
                val displayText = extractByWeight(text, scrollData.scrollOffset, maxOf(remainingWeight, MAX_DISPLAY_WEIGHT))

                val pauseElapsed = nowMillis() - scrollData.initialPauseStartTime
                if (pauseElapsed >= FINAL_PAUSE_DURATION) {
                    scrollData.scrollState = ScrollState.DONE
                }

                displayText
            }

            ScrollState.DONE -> {
                // 保持显示最终片段
                val remainingWeight = totalWeight - scrollData.scrollOffset
                extractByWeight(text, scrollData.scrollOffset, maxOf(remainingWeight, MAX_DISPLAY_WEIGHT))
            }
        }
    }

    /**
     * 检查是否需要更新通知
     */
    fun shouldUpdateNotification(key: String): Boolean {
        val scrollData = scrollDataMap[key] ?: return false
        val now = nowMillis()

        // 节流：50ms限制
        if (now - scrollData.lastUpdateTime < 50) return false

        scrollData.lastUpdateTime = now
        return true
    }

    /**
     * 滚动是否已结束（短文本无需滚动，或长文本已滚到最后一屏）。
     *
     * 供调用方终止周期性刷新：原实现只按固定延迟无限重排，
     * 短文本下内容恒定却仍会周期性地用相同内容重复刷新通知。
     *
     * 状态不存在时返回 false（保守判定为「未结束」），避免误终止尚未开始的滚动。
     */
    fun isScrollFinished(key: String): Boolean {
        val scrollData = scrollDataMap[key] ?: return false
        return scrollData.scrollState == ScrollState.DONE
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
