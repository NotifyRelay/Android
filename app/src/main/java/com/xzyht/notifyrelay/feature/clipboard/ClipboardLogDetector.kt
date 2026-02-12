package com.xzyht.notifyrelay.feature.clipboard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.xzyht.notifyrelay.ui.ClipboardSyncActivity
import notifyrelay.base.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 剪贴板日志检测器
 * 通过读取系统日志来检测剪贴板变化，作为无障碍服务的替代方案
 * 需要 android.permission.READ_LOGS 权限
 */
object ClipboardLogDetector {
    private const val TAG = "ClipboardLogDetector"
    private val monitoringJob = AtomicReference<Job?>(null)
    private val isMonitoring = AtomicBoolean(false)
    private val pausedUntilTime = AtomicLong(0)
    
    /**
     * 暂停检测一段时间（用于防止处理远端消息时产生的自身日志触发循环）
     * @param durationMs 暂停毫秒数
     */
    fun pauseDetectionTemporary(durationMs: Long = 2000) {
        pausedUntilTime.set(System.currentTimeMillis() + durationMs)
        Logger.d(TAG, "暂停日志检测 $durationMs ms")
    }
    
    /**
     * 启动日志监听
     */
    fun startMonitoring(context: Context) {
        // 使用 compareAndSet 实现原子化的检查-设置操作
        if (!isMonitoring.compareAndSet(false, true)) return
        
        try {
            // 检查应用是否处于前台
            val isForeground = notifyrelay.base.util.PermissionHelper.isAppInForeground(context)
            if (!isForeground) {
                Logger.d(TAG, "应用未处于前台，无法启动日志监听")
                isMonitoring.set(false)
                return
            }
            
            // 检查是否有READ_LOGS权限
            if (context.checkSelfPermission(android.Manifest.permission.READ_LOGS) != PackageManager.PERMISSION_GRANTED) {
                Logger.d(TAG, "没有 READ_LOGS 权限，无法启动日志监听")
                isMonitoring.set(false)
                return
            }
            
            Logger.d(TAG, "启动剪贴板日志监听...")
            
            monitoringJob.set(CoroutineScope(Dispatchers.IO).launch {
                var process: Process? = null
                try {
                    // 仅监听 ClipboardService 标签的日志
                    // 加上 -T 1 可能需要较新版本的 logcat，这里直接读取流，依赖 SyncManager 去重
                    val cmd = "logcat -v tag -s ClipboardService"
                    process = Runtime.getRuntime().exec(cmd)
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    
                    var line: String? = null
                    while (isActive && reader.readLine().also { line = it } != null) {
                        try {
                            line?.let { logLine ->
                                // 如果处于暂停期，则忽略
                                if (System.currentTimeMillis() < pausedUntilTime.get()) {
                                    return@let
                                }
                                
                                // 检测剪贴板变化日志
                                // 1. 常规 setPrimaryClip
                                // 2. 本应用访问被拒绝（意味着剪贴板发生了变化且本应用尝试读取了）
                                // "Denying clipboard access to com.xzyht.notifyrelay"
                                val isSetPrimaryClip = logLine.contains("setPrimaryClip")
                                val isAccessDenied = logLine.contains("Denying clipboard access to") && 
                                                   logLine.contains("com.xzyht.notifyrelay")
                                
                                if (isSetPrimaryClip || isAccessDenied) {
                                    // 简单的防抖动或去重可以在这里做，但 SyncManager 已经有了
                                    Logger.d(TAG, "检测到剪贴板变化日志: $logLine")
                                    onClipboardChanged(context)
                                }
                            }
                        } catch (e: SecurityException) {
                            Logger.e(TAG, "处理日志行时发生安全异常", e)
                        } catch (e: Exception) {
                            Logger.e(TAG, "处理日志行时发生异常", e)
                        }
                    }
                } catch (e: SecurityException) {
                    Logger.e(TAG, "启动日志监听时发生安全异常", e)
                } catch (e: Exception) {
                    Logger.e(TAG, "日志监听出错", e)
                } finally {
                    try {
                        process?.destroy()
                    } catch (e: Exception) {
                        Logger.e(TAG, "销毁进程时发生异常", e)
                    }
                    isMonitoring.set(false)
                }
            })
        } catch (e: SecurityException) {
            Logger.e(TAG, "启动日志监听时发生安全异常", e)
            isMonitoring.set(false)
        } catch (e: Exception) {
            Logger.e(TAG, "启动日志监听失败", e)
            isMonitoring.set(false)
        }
    }
    
    /**
     * 停止监听
     */
    fun stopMonitoring() {
        monitoringJob.get()?.cancel()
        monitoringJob.set(null)
        isMonitoring.set(false)
        Logger.d(TAG, "日志监听已停止")
    }
    
    /**
     * 获取日志监听状态
     */
    fun isMonitoring(): Boolean {
        return isMonitoring.get()
    }
    
    private fun onClipboardChanged(context: Context) {
        // 启动透明Activity来获取剪贴板内容
        try {
            Logger.d(TAG, "检测到剪贴板变化，准备启动 ClipboardSyncActivity")
            val intent = Intent(context, ClipboardSyncActivity::class.java)
            // 必须添加 FLAG_ACTIVITY_NEW_TASK，因为是在 Service/BroadcastReceiver 上下文中启动
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // 添加 FLAG_ACTIVITY_CLEAR_TOP 确保之前的实例被清理（虽然是 singleInstance）
            // 同时可以尝试添加 FLAG_ACTIVITY_NO_ANIMATION 减少视觉干扰
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            
            // Android 10+ 后台启动 Activity 限制：
            // 如果应用在后台，且没有 SYSTEM_ALERT_WINDOW 权限，startActivity 可能会被拦截。
            // 但如果应用有 "允许后台弹出界面" (MIUI等) 权限，或者通过 Notification PendingIntent 启动则可以。
            // 这里我们先尝试直接启动。
            
            context.startActivity(intent)
            Logger.d(TAG, "已调用 startActivity")
        } catch (e: Exception) {
            Logger.e(TAG, "启动同步Activity失败", e)
        }
    }
}
