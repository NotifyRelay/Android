package com.xzyht.notifyrelay.feature.media.service

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import notifyrelay.base.util.Logger

/**
 * 媒体会话监控的健康检查与重试机制，独立于 [MediaSessionMonitorService] 的控制器注册逻辑。
 *
 * 依赖均为只读注入（宿主持有真实状态）：
 * - [getMediaSessionManager] / [getComponentName]：读取当前 MediaSessionManager 与 ComponentName
 * - [isConnected]：读取当前连接状态（健康检查在权限丢失时会触发 [onPermissionLost] 写回 false）
 * - [onUpdateControllers]：重试成功后将新控制器列表回调给宿主重新注册
 */
internal class MediaSessionHealthMonitor(
    private val handler: Handler,
    private val getMediaSessionManager: () -> MediaSessionManager?,
    private val getComponentName: () -> ComponentName?,
    private val isConnected: () -> Boolean,
    private val onPermissionLost: () -> Unit,
    private val onUpdateControllers: (controllers: List<MediaController>?) -> Unit,
) {
    companion object {
        private const val TAG = "MediaSessionHealthMonitor"
    }

    // 健康检查机制
    private val healthCheckRunnable =
        object : Runnable {
            override fun run() {
                // 验证连接是否仍然有效
                if (isConnected() && getMediaSessionManager() != null && getComponentName() != null) {
                    try {
                        // 测试访问 - 如果权限被撤销，这将抛出异常
                        getMediaSessionManager()?.getActiveSessions(getComponentName())
                        Logger.i(TAG, "Health Check: OK")
                    } catch (e: SecurityException) {
                        Logger.w(TAG, "Health Check: FAILED - Permission lost")
                        onPermissionLost()
                        // 权限丢失，等待系统重新绑定
                    }
                }
                // 安排下一次检查
                handler.postDelayed(this, 30000)
            }
        }

    // 启动重试机制
    private val startupRetryRunnable =
        object : Runnable {
            override fun run() {
                if (!isConnected()) return

                try {
                    val controllers = getMediaSessionManager()?.getActiveSessions(getComponentName())
                    Logger.i(TAG, "Successfully retrieved ${controllers?.size ?: 0} active sessions")
                    onUpdateControllers(controllers)
                } catch (e: SecurityException) {
                    Logger.w(TAG, "Security Error on initial check: ${e.message}")
                    // 200ms 后重试一次，以防权限仍在授予中
                    handler.postDelayed(retryRunnable, 200)
                }
            }
        }

    // 200ms 重试机制
    private val retryRunnable =
        object : Runnable {
            override fun run() {
                if (!isConnected()) return

                try {
                    val controllers = getMediaSessionManager()?.getActiveSessions(getComponentName())
                    Logger.i(TAG, "Retry successful: ${controllers?.size ?: 0} sessions")
                    onUpdateControllers(controllers)
                } catch (e2: SecurityException) {
                    Logger.e(TAG, "Retry failed: ${e2.message} - Permission may need manual grant")
                }
            }
        }

    // 启动健康检查监控
    fun startHealthCheck() {
        // 健康检查每 30s 自调度
        handler.postDelayed(healthCheckRunnable, 30000)
        // 启动重试机制（100ms 后），确保权限在重新绑定后完全生效
        handler.postDelayed(startupRetryRunnable, 100)
    }

    // 停止健康检查与重试：取消全部三个 Runnable
    fun stop() {
        handler.removeCallbacks(healthCheckRunnable)
        handler.removeCallbacks(startupRetryRunnable)
        handler.removeCallbacks(retryRunnable)
    }
}
