package com.xzyht.notifyrelay.servers

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.xzyht.notifyrelay.servers.clipboard.ClipboardDetection
import com.xzyht.notifyrelay.ui.ClipboardSyncActivity
import notifyrelay.base.util.Logger

/**
 * 剪贴板无障碍服务
 * 用于智能检测复制操作并触发同步
 */
class ClipboardAccessiblityService : AccessibilityService() {

    private val TAG = "ClipboardAccessibility"

    // 剪贴板检测实例
    private lateinit var clipboardDetector: ClipboardDetection

    // 上次检测时间
    private var lastDetectionTimeMs = 0L
    // 最小检测间隔
    private val minDetectionInterval = 100L

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "剪贴板无障碍服务已创建")
        // 初始化剪贴板检测器
        clipboardDetector = ClipboardDetection()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        // 配置无障碍服务信息
        val info = AccessibilityServiceInfo().apply {
            // 设置事件类型 - 监听多种事件
            eventTypes = MONITORED_EVENTS

            // 设置反馈类型 - 使用0表示无反馈
            feedbackType = 0

            // 设置标志 - 只保留必要的标志
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS

            // 设置通知超时时间
            notificationTimeout = 120
        }

        serviceInfo = info
        Logger.i(TAG, "剪贴板无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val currentTimeMs = System.currentTimeMillis()

            if (currentTimeMs < pausedUntilTime) {
                return
            }

            val packageName = event.packageName?.toString() ?: "unknown"
            val eventType = when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> "VIEW_CLICKED"
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> "VIEW_FOCUSED"
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "VIEW_LONG_CLICKED"
                AccessibilityEvent.TYPE_VIEW_SELECTED -> "VIEW_SELECTED"
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "VIEW_TEXT_CHANGED"
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> "VIEW_TEXT_SELECTION_CHANGED"
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> "NOTIFICATION_STATE_CHANGED"
                else -> "OTHER(${event.eventType})"
            }
            
            Logger.d(TAG, "收到事件: $eventType, 包名: $packageName, 类名: ${event.className}, 文本: ${event.text}, 描述: ${event.contentDescription}")

            if (clipboardDetector.getSupportedEventTypes(event)) {
                if (currentTimeMs - lastDetectionTimeMs < minDetectionInterval) {
                    return
                }

                lastDetectionTimeMs = currentTimeMs
                Logger.i(TAG, "检测到复制事件，启动透明Activity")
                startTransparentActivity()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "无障碍服务错误", e)
        }
    }

    /**
     * 启动透明Activity获取剪贴板
     */
    private fun startTransparentActivity() {
        try {
            val intent = Intent(this, ClipboardSyncActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            startActivity(intent)
            Logger.d(TAG, "透明Activity已启动")
        } catch (e: Exception) {
            Logger.e(TAG, "启动透明Activity失败: ${e.message}", e)
        }
    }

    override fun onInterrupt() {
        Logger.w(TAG, "剪贴板无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(TAG, "剪贴板无障碍服务已销毁")
    }

    companion object {
        // 暂停截止时间
        @Volatile
        private var pausedUntilTime: Long = 0

        /**
         * 暂时暂停检测（用于防止循环同步）
         * @param durationMs 暂停时长（毫秒）
         */
        fun pauseDetectionTemporary(durationMs: Long) {
            pausedUntilTime = System.currentTimeMillis() + durationMs
            Logger.d("ClipboardAccessibility", "无障碍服务检测已暂停 $durationMs ms")
        }

        // 监听的事件类型
        private const val MONITORED_EVENTS = AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or
                AccessibilityEvent.TYPE_VIEW_SELECTED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
    }
}