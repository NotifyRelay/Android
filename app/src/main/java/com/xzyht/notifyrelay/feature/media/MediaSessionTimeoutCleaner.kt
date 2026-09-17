package com.xzyht.notifyrelay.feature.media

import android.content.Context
import android.os.Handler
import notifyrelay.base.util.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * 超时会话清理循环（plan.md「步骤 2」抽取）。
 *
 * 持有清理循环的状态（[cleanupRunnable] / [cleanupLoopRunning]）与超时扫描逻辑，但所有会话相关业务状态
 * （[MediaSessionCacheDataHolder] 缓存、featureId/lastUpdate 缓存、current 指针）仍由
 * [RemoteMediaSessionManager] 持有，清理时通过 [CleanupHost] 回调由其统一执行「关闭单设备会话」的拆卸，
 * 避免在多个对象间复制同一段拆卸逻辑。
 *
 * 关键约束（plan.md 反复强调）：
 * - 全部状态读写串行在 main-looper handler 上。[cleanupLoopRunning] 只在本对象内由 handler 线程读写，
 *   不改为 `@Volatile` 跨线程访问（此处保留原样，未提升可见性语义）。
 * - [ensureCleanupLoop] 是 post 版本（跨线程调用时先 post 进 handler）；[ensureCleanupLoopOnHandler]
 *   是直调版本（已在 handler 线程内调用，不可再 post，否则时序错误）。两者区分不可混淆。
 * - 传入的 [Handler] 与 [RemoteMediaSessionManager] 同源，保证 callback 调度与移除在同一线程。
 */
object MediaSessionTimeoutCleaner {
    // 超时时间（毫秒），与发送端超时发送时间匹配并略长（16秒）
    private const val MEDIA_SESSION_TIMEOUT_MS = 16 * 1000L

    // 定时检查超时会话的间隔（毫秒）
    private const val CLEANUP_INTERVAL_MS = 3 * 1000L

    // 定期检查超时会话的任务
    private var cleanupRunnable: Runnable? = null

    // 清理循环是否运行中（有活跃会话时才运行，无会话即停止，避免常驻空转）
    // 访问保护：所有读写都通过 handler 串行执行
    private var cleanupLoopRunning = false

    // 宿主回调：提供同源 handler、应用上下文、lastUpdate 缓存，以及统一的单设备会话拆卸
    private var host: CleanupHost? = null

    interface CleanupHost {
        val handler: Handler
        val applicationContext: Context?
        val mediaLastUpdateTime: ConcurrentHashMap<String, Long>

        // 关闭单个设备会话（移除 Store/浮窗/缓存/当前指针），由 manager 统一实现以保证单一来源
        fun closeSessionByUuid(deviceUuid: String)
    }

    fun bind(host: CleanupHost) {
        this.host = host
    }

    // 创建定期检查任务
    private fun createCleanupRunnable(): Runnable =
        Runnable {
            try {
                // 使用保存的应用上下文
                val context = host?.applicationContext
                if (context != null) {
                    cleanupTimeoutSessionsOnHandler(context)
                } else {
                    Logger.w("RemoteMediaSessionManager", "应用上下文未初始化，跳过定期检查")
                }
            } catch (e: Exception) {
                Logger.e("RemoteMediaSessionManager", "定期检查超时会话失败", e)
            } finally {
                // 仍有活跃会话才继续调度，否则停止循环（已在 handler 线程，直接读写）
                if (cleanupLoopRunning) {
                    cleanupRunnable?.let { host?.handler?.postDelayed(it, CLEANUP_INTERVAL_MS) }
                }
            }
        }

    /**
     * 确保超时会话清理循环在运行（有活跃媒体会话时调用）
     * 通过 handler 串行执行，保护 cleanupLoopRunning 读写和 callback 调度。
     */
    fun ensureCleanupLoop() {
        val h = host?.handler ?: return
        h.post {
            if (cleanupLoopRunning) {
                return@post
            }
            cleanupLoopRunning = true
            val runnable = cleanupRunnable ?: createCleanupRunnable().also { cleanupRunnable = it }
            h.removeCallbacks(runnable)
            h.postDelayed(runnable, CLEANUP_INTERVAL_MS)
        }
    }

    /**
     * 停止超时会话清理循环（无活跃会话时）
     * 通过 handler 串行执行，保护 cleanupLoopRunning 读写和 callback 调度。
     */
    fun stopCleanupLoop() {
        val h = host?.handler ?: return
        h.post {
            cleanupLoopRunning = false
            cleanupRunnable?.let { h.removeCallbacks(it) }
        }
    }

    /**
     * 确保清理循环运行（handler 线程内部调用，无需再 post）
     */
    fun ensureCleanupLoopOnHandler() {
        val h = host?.handler ?: return
        if (cleanupLoopRunning) {
            return
        }
        cleanupLoopRunning = true
        val runnable = cleanupRunnable ?: createCleanupRunnable().also { cleanupRunnable = it }
        h.removeCallbacks(runnable)
        h.postDelayed(runnable, CLEANUP_INTERVAL_MS)
    }

    /**
     * 检查并清理超时的媒体会话（handler 线程内部调用）
     */
    fun cleanupTimeoutSessionsOnHandler(context: Context) {
        val h = host ?: return
        val currentTime = System.currentTimeMillis()
        val timeoutDevices = mutableListOf<String>()

        // 找出超时的设备
        for ((deviceUuid, lastUpdateTime) in h.mediaLastUpdateTime) {
            if (currentTime - lastUpdateTime > MEDIA_SESSION_TIMEOUT_MS) {
                timeoutDevices.add(deviceUuid)
            }
        }

        // 清理超时会话
        for (deviceUuid in timeoutDevices) {
            h.closeSessionByUuid(deviceUuid)
            Logger.i("RemoteMediaSessionManager", "已清理超时的媒体会话: $deviceUuid")
        }

        // 无任何活跃会话时停止清理循环，避免常驻空转（已在 handler 线程，直接执行）
        if (h.mediaLastUpdateTime.isEmpty()) {
            cleanupLoopRunning = false
            cleanupRunnable?.let { h.handler.removeCallbacks(it) }
        }
    }
}
