package com.xzyht.notifyrelay.ui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.xzyht.notifyrelay.feature.clipboard.ClipboardSyncManager
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
import notifyrelay.base.util.Logger

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

        // 移除 setContentView 和 viewTreeObserver 监听，减少UI渲染
        // 直接设置不可触摸标志
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            performClipboardSyncOnUIThread()
        }
    }

    private fun performClipboardSyncOnUIThread() {
        // 在UI线程中直接执行，确保能访问剪贴板
        runOnUiThread {
            try {
                // 1. 直接在UI线程获取剪贴板数据
                val clipboardData = getCurrentClipboardData()

                if (clipboardData != null) {
                    // 2. 获取设备管理器实例
                    val deviceManager = DeviceConnectionManagerSingleton.getDeviceManager(this)

                    // 3. 发送剪贴板数据
                    sendClipboardData(deviceManager, clipboardData)
                }
                // 移除Toast，减少干扰
            } catch (e: SecurityException) {
                Logger.e(TAG, "剪贴板访问被拒绝", e)
            } catch (e: Exception) {
                Logger.e(TAG, "同步失败", e)
            } finally {
                // 无论成功与否，都立即结束Activity
                finish()
                // 禁用退出动画，实现无感关闭
                overridePendingTransition(0, 0)
            }
        }
    }

    /**
     * 直接在UI线程获取剪贴板数据
     */
    private fun getCurrentClipboardData(): Pair<String, String>? {
        try {
            val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

            // 检查剪贴板是否有内容
            if (!clipboardManager.hasPrimaryClip()) {
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
                        return Pair(CLIPBOARD_TYPE_TEXT, text)
                    }
                }
            }
        } catch (e: SecurityException) {
            throw e // 重新抛出，让调用者处理
        } catch (e: Exception) {
            // 忽略异常，返回null
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