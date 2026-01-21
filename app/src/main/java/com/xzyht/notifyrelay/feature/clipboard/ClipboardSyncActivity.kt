package com.xzyht.notifyrelay.feature.clipboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.xzyht.notifyrelay.common.core.util.Logger
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton

/**
 * 透明Activity，用于解决Android 10+的剪贴板访问限制
 * 当应用不在前台时，通过启动此透明Activity获取焦点，从而能够访问剪贴板
 */
class ClipboardSyncActivity : AppCompatActivity() {
    
    private val TAG = "ClipboardSyncActivity"
    private val CLIPBOARD_TYPE_TEXT = "text"
    private val CLIPBOARD_TYPE_IMAGE = "image"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置透明主题（在AndroidManifest.xml中配置）
        Logger.d(TAG, "透明Activity创建，准备获取剪贴板数据")
        
        // 添加一个空的View，确保Activity能正常获得焦点
        setContentView(android.R.layout.activity_list_item)
        
        // 注册焦点变化监听器
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener {
                oldFocus, newFocus -> 
            Logger.d(TAG, "Global focus changed: old=$oldFocus, new=$newFocus")
            if (newFocus != null) {
                performClipboardSyncOnUIThread()
            }
        }
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Logger.d(TAG, "onWindowFocusChanged: hasFocus=$hasFocus")
        
        if (hasFocus) {
            Logger.d(TAG, "✅ 窗口获得焦点，开始读取剪贴板")
            performClipboardSyncOnUIThread()
        }
    }
    
    private fun performClipboardSyncOnUIThread() {
        // 在UI线程中直接执行，确保能访问剪贴板
        runOnUiThread {
            try {
                Logger.d(TAG, "开始执行剪贴板同步（UI线程）")
                
                // 1. 直接在UI线程获取剪贴板数据
                val clipboardData = getCurrentClipboardData()
                
                if (clipboardData != null) {
                    Logger.d(TAG, "✅ 剪贴板读取成功")
                    
                    // 2. 获取设备管理器实例
                    val deviceManager = DeviceConnectionManagerSingleton.getDeviceManager(this)
                    
                    // 3. 发送剪贴板数据
                    sendClipboardData(deviceManager, clipboardData)
                } else {
                    Logger.e(TAG, "❌ 剪贴板为空或无法获取")
                    Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Logger.e(TAG, "❌ 剪贴板访问被拒绝：${e.message}")
                Toast.makeText(this, "剪贴板访问被拒绝", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Logger.e(TAG, "❌ 剪贴板同步失败：${e.message}", e)
                Toast.makeText(this, "同步失败", Toast.LENGTH_SHORT).show()
            } finally {
                // 无论成功与否，都立即结束Activity
                Logger.d(TAG, "剪贴板同步执行完成，关闭Activity")
                finish()
            }
        }
    }
    
    /**
     * 直接在UI线程获取剪贴板数据
     */
    private fun getCurrentClipboardData(): Pair<String, String>? {
        try {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            
            // 检查剪贴板是否有内容
            if (!clipboardManager.hasPrimaryClip()) {
                Logger.d(TAG, "剪贴板为空")
                return null
            }
            
            val clip = clipboardManager.primaryClip
            val clipDescription = clip?.description
            val item = clip?.getItemAt(0)
            
            if (clipDescription != null && item != null) {
                // 处理文本类型
                if (clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                    clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) {
                    
                    val text = item.text?.toString()
                    if (!text.isNullOrEmpty()) {
                        Logger.d(TAG, "获取到文本剪贴板：$text")
                        return Pair(CLIPBOARD_TYPE_TEXT, text)
                    }
                }
            }
        } catch (e: SecurityException) {
            Logger.e(TAG, "获取剪贴板失败：权限拒绝", e)
            throw e // 重新抛出，让调用者处理
        } catch (e: Exception) {
            Logger.e(TAG, "获取剪贴板失败：${e.message}", e)
        }
        return null
    }
    
    /**
     * 发送剪贴板数据到设备
     */
    private fun sendClipboardData(
        deviceManager: DeviceConnectionManager,
        clipboardData: Pair<String, String>
    ) {
        // 直接使用ClipboardSyncManager的手动同步方法
        ClipboardSyncManager.manualSyncClipboard(deviceManager, this)
    }
}