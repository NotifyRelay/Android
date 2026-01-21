package com.xzyht.notifyrelay.feature.clipboard

import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.xzyht.notifyrelay.common.core.util.Logger
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 剪贴板监控服务
 * 负责在后台持续监听剪贴板变化并同步到其他设备
 * 通知管理由 NotifyRelayNotificationListenerService 的前台服务统一处理
 */
class ClipboardMonitorService : Service() {
    private val TAG = "ClipboardMonitorService"
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var clipboardManager: ClipboardManager? = null
    private var deviceManager: DeviceConnectionManager? = null
    private var isMonitoring = false
    
    override fun onCreate() {
        super.onCreate()
        Logger.d(TAG, "剪贴板监控服务已创建")
        
        // 初始化剪贴板管理器
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        // 初始化设备连接管理器
        deviceManager = DeviceConnectionManager.getInstance(this)
        
        // 初始化剪贴板同步管理器
        ClipboardSyncManager.init(this)
        
        // 开始监控剪贴板
        startMonitoring()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.d(TAG, "剪贴板监控服务已启动")
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Logger.d(TAG, "剪贴板监控服务已销毁")
        isMonitoring = false
    }
    
    /**
     * 开始监控剪贴板变化
     */
    private fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        
        // 使用协程定期检查剪贴板变化
        serviceScope.launch {
            while (isMonitoring) {
                try {
                    // 发送当前剪贴板内容到其他设备
                    deviceManager?.let {
                        ClipboardSyncManager.sendClipboardToDevices(it, this@ClipboardMonitorService)
                    }
                    
                    // 每2秒检查一次剪贴板变化
                    delay(2000)
                } catch (e: SecurityException) {
                    Logger.w(TAG, "剪贴板访问权限被拒绝，应用可能不在前台", e)
                    // 权限拒绝时，延长检查间隔，减少系统负担
                    delay(5000)
                } catch (e: Exception) {
                    Logger.e(TAG, "剪贴板监控服务发生错误", e)
                    // 其他异常时，短暂延迟后继续监控
                    delay(10000)
                }
            }
        }
        
        // 监听剪贴板变化事件（Android 12+ 支持）
        clipboardManager?.addPrimaryClipChangedListener {
            Logger.d(TAG, "剪贴板内容已改变，正在发送到其他设备")
            serviceScope.launch {
                try {
                    deviceManager?.let {
                        ClipboardSyncManager.sendClipboardToDevices(it, this@ClipboardMonitorService)
                    }
                } catch (e: SecurityException) {
                    Logger.w(TAG, "剪贴板变化监听：权限拒绝", e)
                } catch (e: Exception) {
                    Logger.e(TAG, "剪贴板变化监听：发生错误", e)
                }
            }
        }
    }
}
