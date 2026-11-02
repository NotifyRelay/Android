package com.xzyht.notifyrelay.ui.activity

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.xzyht.notifyrelay.feature.clipboard.ClipboardSyncManager
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
import notifyrelay.base.log.Logger

/**
 * 透明Activity，用于解决Android 10+的剪贴板访问限制
 * 当应用不在前台时，通过启动此透明Activity获取焦点，从而能够访问剪贴板
 *
 * 读取必须在 UI 线程同步完成：本 Activity 一旦失去焦点就再也读不到剪贴板。
 * 读取与发送都走 [ClipboardSyncManager] 的同一实现，文本与图片同路径。
 */
class ClipboardSyncActivity : AppCompatActivity() {
    private val tag = "ClipboardSyncActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置透明主题（在AndroidManifest.xml中配置）
        Logger.d(tag, "透明Activity创建，准备获取剪贴板数据")

        // 移除 setContentView 和 viewTreeObserver 监听，减少UI渲染
        // 直接设置不可触摸标志
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
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
                // 1. 在UI线程同步读取剪贴板内容
                val clipboardData = ClipboardSyncManager.readPrimaryClip(this)

                if (clipboardData != null) {
                    // 2. 获取设备管理器实例
                    val deviceManager = DeviceConnectionManagerSingleton.getDeviceAdmin(this)

                    // 3. 交给统一发送入口（force=true 跳过内容未变检查）
                    ClipboardSyncManager.sendClipboardData(
                        deviceManager,
                        clipboardData.first,
                        clipboardData.second,
                        this,
                        force = true,
                    )
                }
                // 移除Toast，减少干扰
            } catch (e: SecurityException) {
                Logger.e(tag, "剪贴板访问被拒绝", e)
            } catch (e: Exception) {
                Logger.e(tag, "同步失败", e)
            } finally {
                // 无论成功与否，都立即结束Activity
                finish()
                overridePendingTransition(0, 0)
            }
        }
    }
}
