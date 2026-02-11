package com.xzyht.notifyrelay.feature.clipboard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.xzyht.notifyrelay.feature.ClipboardSyncActivity
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
        
        try {
            val currentTimeMs = System.currentTimeMillis()
            
            // 如果处于暂停状态，直接忽略事件
            if (currentTimeMs < pausedUntilTime) {
                return
            }
            
            // Android 10+ 检测复制操作
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && 
                clipboardDetector.getSupportedEventTypes(event)) {
                
                // 防抖处理
                if (currentTimeMs - lastDetectionTimeMs < minDetectionInterval) {
                    return
                }
                
                lastDetectionTimeMs = currentTimeMs
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
