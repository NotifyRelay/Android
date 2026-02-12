package com.xzyht.notifyrelay.servers

import android.app.Service
import android.content.ClipboardManager
import android.content.Intent
import android.os.IBinder
import com.xzyht.notifyrelay.servers.clipboard.ClipboardSyncManager
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.ui.ClipboardSyncActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import notifyrelay.base.util.Logger

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
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

        // 初始化设备连接管理器
        deviceManager = DeviceConnectionManager.Companion.getInstance(this)

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

        // 监听剪贴板变化事件（Android 12+ 支持）
        clipboardManager?.addPrimaryClipChangedListener {
            Logger.d(TAG, "剪贴板内容已改变，打开透明活动获取剪贴板")
            // 打开透明活动获取剪贴板内容
            val intent = Intent(this@ClipboardMonitorService, ClipboardSyncActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            startActivity(intent)
        }
    }
}