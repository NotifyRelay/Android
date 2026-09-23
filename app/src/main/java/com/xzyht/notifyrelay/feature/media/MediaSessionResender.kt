package com.xzyht.notifyrelay.feature.media

import android.content.Context
import android.os.Handler
import com.xzyht.notifyrelay.feature.device.model.DeviceInfo
import github.xzynine.superislandui.model.components.MediaSessionData
import notifyrelay.base.util.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * 媒体会话定时复传（plan.md「步骤 3」抽取）。
 *
 * [setupResendTask] 为自递归调度：Runnable 内部再次调 [setupResendTask] 实现 6s 周期复传。
 * [cancelResendTask] 依赖 [mediaSessionCache] 中 `deviceUuid` 对应的 `resendRunnable` 才能
 * `handler.removeCallbacks` 取消——因此 [mediaSessionCache] 与此处使用的 [Handler] 必须同源。
 *
 * 为保持「map 与 handler 同源」且不引入第二份会话缓存（[RemoteMediaSessionManager] 的
 * processMediaMessageOnHandler 还需读同一份缓存做 oldSession 兜底），[mediaSessionCache] 由 manager
 * 持有并传入，本对象不另持副本。本对象只持有复传相关的常量与逻辑。
 */
internal object MediaSessionResender {
    // 定时复传间隔（毫秒），设置为6秒，确保在12秒自动关闭前更新两次
    private const val MEDIA_SESSION_RESEND_INTERVAL_MS = 6 * 1000L

    // 接近超时即停止复传的提前量（毫秒）：> TIMEOUT-1000 时停止
    private const val RESEND_STOP_BEFORE_TIMEOUT_MS = 1000L

    /**
     * 创建或更新定时复传任务。
     * @param handler 与调用方同源的 main-looper handler（移除回调用）
     * @param mediaLastUpdateTime 同源的最后更新时间 map（复传守卫读取）
     * @param mediaSessionCache 同源的会话缓存（存放 resendRunnable 供取消）
     * @param sourceKeyPrefix 固定 sourceKey 前缀
     * @param timeoutMs 超时时间（毫秒），用于「接近超时停止复传」守卫
     */
    fun setupResendTask(
        handler: Handler,
        mediaLastUpdateTime: ConcurrentHashMap<String, Long>,
        mediaSessionCache: ConcurrentHashMap<String, MediaSessionCacheDataHolder>,
        sourceKeyPrefix: String,
        context: Context,
        deviceUuid: String,
        session: MediaSessionData,
        device: DeviceInfo,
        timeoutMs: Long,
    ) {
        cancelResendTask(handler, mediaSessionCache, deviceUuid)

        val resendRunnable =
            Runnable {
                try {
                    val originalLastUpdateTime = mediaLastUpdateTime[deviceUuid] ?: System.currentTimeMillis()
                    if (System.currentTimeMillis() - originalLastUpdateTime > (timeoutMs - RESEND_STOP_BEFORE_TIMEOUT_MS)) {
                        Logger.i("RemoteMediaSessionManager", "媒体会话已接近超时，停止复传: $deviceUuid")
                        return@Runnable
                    }

                    val sourceKey = sourceKeyPrefix + "_" + deviceUuid
                    val currentState = MediaStateApplier.buildMediaState(session.title, session.text, session.coverUrl)
                    MediaStateApplier.applyMediaSessionState(sourceKey, currentState, session.appName, context)

                    setupResendTask(
                        handler,
                        mediaLastUpdateTime,
                        mediaSessionCache,
                        sourceKeyPrefix,
                        context,
                        deviceUuid,
                        session,
                        device,
                        timeoutMs,
                    )
                } catch (e: Exception) {
                    Logger.e("RemoteMediaSessionManager", "定时复传媒体会话失败: $deviceUuid", e)
                }
            }

        handler.postDelayed(resendRunnable, MEDIA_SESSION_RESEND_INTERVAL_MS)
        mediaSessionCache[deviceUuid] =
            MediaSessionCacheDataHolder(
                context = context,
                session = session,
                device = device,
                resendRunnable = resendRunnable,
            )
    }

    /**
     * 取消定时复传任务。依赖 mediaSessionCache 中缓存的 resendRunnable 才能 handler.removeCallbacks。
     */
    fun cancelResendTask(
        handler: Handler,
        mediaSessionCache: ConcurrentHashMap<String, MediaSessionCacheDataHolder>,
        deviceUuid: String,
    ) {
        val cacheData = mediaSessionCache.remove(deviceUuid)
        if (cacheData != null) {
            handler.removeCallbacks(cacheData.resendRunnable)
        }
    }
}

/**
 * 媒体会话缓存数据类（复传用途），定义在文件顶层便于 [RemoteMediaSessionManager] 复用同一份缓存。
 */
internal data class MediaSessionCacheDataHolder(
    val context: Context,
    val session: MediaSessionData,
    val device: DeviceInfo,
    val resendRunnable: Runnable,
)
