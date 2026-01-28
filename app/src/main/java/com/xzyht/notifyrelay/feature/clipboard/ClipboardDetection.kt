/*
 * Acknowledgment:
 * Portions of this code are adapted from XClipper by Kaustubh Patange.
 * Licensed under the Apache License 2.0.
 */

package com.xzyht.notifyrelay.feature.clipboard

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayList

/**
 * 事件检测谓词
 */
typealias Predicate = (ClipboardDetection.AEvent) -> Boolean

/**
 * 剪贴板复制检测类
 * 用于智能分析无障碍事件，检测各种复制操作
 */
class ClipboardDetection(
    private val copyWord: String = "复制"
) {
    private val typeViewSelectionChangeEvent: StripArrayList<AEvent> = StripArrayList(2)
    private val eventList: StripArrayList<Int> = StripArrayList(4)
    private var lastEvent: AEvent? = null

    /**
     * 添加无障碍事件到事件列表
     */
    fun addEvent(c: Int) {
        eventList.add(c)
    }

    /**
     * 检测是否为支持的事件类型，用于触发剪贴板同步
     */
    fun getSupportedEventTypes(event: AccessibilityEvent?, predicate: Predicate? = null): Boolean {
        if (event == null) return false

        val clipEvent = AEvent.from(event)
        if (predicate?.invoke(clipEvent) == true) return false
        return detectAppropriateEvents(event = clipEvent)
    }

    /**
     * 检测合适的复制事件
     */
    private fun detectAppropriateEvents(event: AEvent): Boolean {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            typeViewSelectionChangeEvent.add(event)
        }

        // 检测点击复制/剪切按钮的行为
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && event.text != null
            && ((event.contentDescription?.length ?: 0) < MAX_COPY_WORD_DETECTION_LENGTH
                    && event.contentDescription?.contains(copyWord, true) == true
                    || ((event.text?.toString()?.length ?: 0) < MAX_COPY_WORD_DETECTION_LENGTH
                    && event.text?.toString()?.contains(copyWord, true) == true)
                    || event.contentDescription == "Cut" || event.contentDescription == copyWord)
        ) {
            return true
        }

        // 检测文本选择完成后的复制行为
        if (typeViewSelectionChangeEvent.size == 2) {
            val firstEvent = typeViewSelectionChangeEvent[0]
            val secondEvent = typeViewSelectionChangeEvent[1]
            if (secondEvent.fromIndex == secondEvent.toIndex) {
                val success = 
                    (firstEvent.packageName == secondEvent.packageName && firstEvent.fromIndex != firstEvent.toIndex
                            && secondEvent.className == firstEvent.className) && secondEvent.text.toString() == firstEvent.text.toString()
                typeViewSelectionChangeEvent.clear()
                if (success) {
                    return true
                }
            }
        }

        // 检测窗口内容变化相关的复制行为
        if ((event.contentChangeTypes ?: 0) and AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE != 0
            && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && lastEvent != null
        ) {
            val previousEvent = lastEvent!!

            if (previousEvent.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && previousEvent.text?.size == 1
                && (previousEvent.text?.toString()?.contains(copyWord, true) == true
                        || previousEvent.contentDescription?.contains(copyWord, true) == true)) {
                return true
            }
        }
        
        // 检测Toast通知中的复制提示
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED && event.className?.toString()?.contains("Toast") == true
            && event.text != null && event.text?.toString()?.contains(AEvent.copyKeyWords) == true) {
            return true
        }

        lastEvent = event.clone()
        return false
    }

    /**
     * 剪贴板事件数据类
     */
    data class AEvent(
        var eventType: Int? = null,
        var eventTime: Long? = null,
        var packageName: CharSequence? = null,
        var movementGranularity: Int? = null,
        var action: Int? = null,
        var className: CharSequence? = null,
        var text: List<CharSequence?>? = null,
        var contentDescription: CharSequence? = null,
        var contentChangeTypes: Int? = null,
        var currentItemIndex: Int? = null,
        var fromIndex: Int? = null,
        var toIndex: Int? = null,
        var scrollX: Int? = null,
        var scrollY: Int? = null,
        var sourceActions: List<AccessibilityNodeInfo.AccessibilityAction> = emptyList(),
    ) {
        companion object {
            val copyKeyWords = "(copied)|(Copied)|(clipboard)|(复制成功)|(已复制)".toRegex()

            fun from(event: AccessibilityEvent): AEvent {
                return AEvent(
                    eventType = event.eventType,
                    eventTime = event.eventTime,
                    packageName = event.packageName,
                    movementGranularity = event.movementGranularity,
                    action = event.action,
                    className = event.className,
                    text = event.text,
                    contentChangeTypes = event.contentChangeTypes,
                    contentDescription = event.contentDescription,
                    currentItemIndex = event.currentItemIndex,
                    fromIndex = event.fromIndex,
                    toIndex = event.toIndex,
                    scrollX = event.scrollX,
                    scrollY = event.scrollY,
                    sourceActions = event.source?.actionList ?: emptyList()
                )
            }
        }
    }

    /**
     * 复制事件
     */
    private fun AEvent.clone(): AEvent = this.copy(text = ArrayList(this.text ?: listOf()))

    /**
     * 带限制的ArrayList，自动移除旧元素
     */
    class StripArrayList<T>(private val maxSize: Int) : ArrayList<T>() {
        override fun add(element: T): Boolean {
            val result = super.add(element)
            if (maxSize > 0 && size > maxSize) {
                removeAt(0)
            }
            return result
        }
    }

    companion object {
        private const val TAG = "ClipboardDetector"
        private const val MAX_COPY_WORD_DETECTION_LENGTH = 30
    }
}