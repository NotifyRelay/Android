package com.xzyht.notifyrelay.feature.clipboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import notifyrelay.core.util.Logger
import notifyrelay.core.util.ToastUtils
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton

/**
 * 剪贴板同步广播接收器
 * 处理通知中的点击操作
 */
class ClipboardSyncReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ClipboardSyncReceiver"
        const val ACTION_MANUAL_SYNC = "com.xzyht.notifyrelay.CLIPBOARD_MANUAL_SYNC"
        const val ACTION_OPEN_SETTINGS = "com.xzyht.notifyrelay.CLIPBOARD_OPEN_SETTINGS"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Logger.d(TAG, "收到广播: ${intent.action}")
        
        when (intent.action) {
            ACTION_MANUAL_SYNC -> {
                handleManualSync(context)
            }
            ACTION_OPEN_SETTINGS -> {
                handleOpenSettings(context)
            }
        }
    }
    
    private fun handleManualSync(context: Context) {
        Logger.d(TAG, "执行手动同步")
        
        try {
            val deviceManager = DeviceConnectionManagerSingleton.getDeviceManager(context)
            val onlineDevices = deviceManager.getAuthenticatedOnlineCount()
            
            if (onlineDevices == 0) {
                ToastUtils.showShortToast(context, "没有已连接的设备，无法同步剪贴板")
                Logger.d(TAG, "没有已连接的设备，跳过同步")
                return
            }
            
            // 启动透明Activity来获取剪贴板数据（解决Android 10+的剪贴板访问限制）
            val syncIntent = Intent(context, ClipboardSyncActivity::class.java)
            syncIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            context.startActivity(syncIntent)
            
            ToastUtils.showShortToast(context, "剪贴板同步成功")
            Logger.d(TAG, "手动同步成功")
        } catch (e: Exception) {
            Logger.e(TAG, "手动同步失败", e)
            ToastUtils.showShortToast(context, "同步失败：${e.message}")
        }
    }
    
    private fun handleOpenSettings(context: Context) {
        Logger.d(TAG, "打开无障碍服务设置")
        
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.e(TAG, "打开无障碍设置失败", e)
            try {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (e2: Exception) {
                Logger.e(TAG, "打开应用设置也失败", e2)
            }
        }
    }
}
