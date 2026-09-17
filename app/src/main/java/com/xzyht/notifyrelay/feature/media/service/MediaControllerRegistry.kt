package com.xzyht.notifyrelay.feature.media.service

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import notifyrelay.base.util.Logger
import java.util.Objects

/**
 * 媒体控制器注册表：管理活跃 [MediaController] 的注册/注销、去重、主控制器选择，
 * 以及会话元数据变化时的兜底上报。
 *
 * 依赖通过注入解耦（宿主持有真实状态）：
 * - [isConnected]：读取连接状态（updateControllers 入口守卫）
 * - [onMetadataUpdate]：主控制器元数据变化时回调宿主 [updateMetadataIfPrimary]
 * - [onRecheckSessions]：控制器会话销毁时触发宿主强制刷新
 */
class MediaControllerRegistry(
    private val handler: Handler,
    private val isConnected: () -> Boolean,
    private val getLastMetadataHash: () -> Int,
    private val onMetadataUpdate: (controller: MediaController) -> Unit,
    private val onRecheckSessions: () -> Unit,
) {
    companion object {
        private const val TAG = "MediaControllerRegistry"
    }

    // 存储当前活跃的媒体控制器
    private val activeControllers = mutableListOf<MediaController>()

    // 存储控制器的回调，用于后续注销
    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()

    // 去重：跟踪最后一个控制器签名
    private var lastControllerSignatures: String = ""

    private fun updateControllers(controllers: List<MediaController>?) {
        // 确保服务仍在运行
        if (!isConnected()) return

        // 去重检查
        val currentSignatures = controllers?.joinToString("|") { "${it.packageName}@${it.hashCode()}" } ?: "null"
        if (currentSignatures == lastControllerSignatures) {
            Logger.v(TAG, "Duplicate session update ignored.")
            return
        }
        lastControllerSignatures = currentSignatures
        Logger.d(TAG, "Processing new session update: $currentSignatures")

        // 健壮更新：清除并替换
        synchronized(activeControllers) {
            // 1. 注销所有旧的
            controllerCallbacks.forEach { (controller, callback) ->
                try {
                    controller.unregisterCallback(callback)
                } catch (e: Exception) {
                    // 忽略
                }
            }
            controllerCallbacks.clear()
            activeControllers.clear()

            // 2. 注册所有新的（如果有效）
            if (controllers != null) {
                controllers.forEach { controller ->
                    try {
                        val callback =
                            object : MediaController.Callback() {
                                override fun onPlaybackStateChanged(state: PlaybackState?) {
                                    // 优先级可能已更改
                                    val primary = getPrimaryController()
                                    if (primary != null && primary.packageName == controller.packageName) {
                                        // 多数应用（尤其车载/NAS/第三方播放器）不回调 onMetadataChanged，
                                        // 歌词更新只能在此兜底：手动比对元数据哈希，变化即按新元数据处理
                                        val meta = primary.metadata
                                        if (meta != null) {
                                            val artHash = (meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART))?.hashCode() ?: 0
                                            val currentHash =
                                                Objects.hash(
                                                    meta.getString(MediaMetadata.METADATA_KEY_TITLE),
                                                    meta.getString(MediaMetadata.METADATA_KEY_ARTIST),
                                                    primary.packageName,
                                                    meta.getLong(MediaMetadata.METADATA_KEY_DURATION),
                                                    artHash,
                                                )
                                            if (currentHash != getLastMetadataHash()) {
                                                onMetadataUpdate(primary)
                                            }
                                        }
                                    }
                                }

                                override fun onMetadataChanged(metadata: MediaMetadata?) {
                                    onMetadataUpdate(controller)
                                }

                                override fun onSessionDestroyed() {
                                    handler.post {
                                        onRecheckSessions() // 强制完全刷新
                                    }
                                }
                            }

                        controller.registerCallback(callback)
                        controllerCallbacks[controller] = callback
                        activeControllers.add(controller)
                    } catch (e: Exception) {
                        Logger.e(TAG, "Failed to hook controller: ${controller.packageName}", e)
                    }
                }
            }
        }

        // 初始检查
        // 强制从主控制器更新
        val primary = getPrimaryController()
        if (primary != null) {
            onMetadataUpdate(primary)
        }
    }

    fun getPrimaryController(): MediaController? {
        synchronized(activeControllers) {
            // 优先级 1：正在播放/缓冲/跳过的控制器
            val playingController =
                activeControllers.firstOrNull {
                    val st = it.playbackState?.state
                    st == PlaybackState.STATE_PLAYING ||
                        st == PlaybackState.STATE_BUFFERING ||
                        st == PlaybackState.STATE_CONNECTING ||
                        st == PlaybackState.STATE_SKIPPING_TO_NEXT ||
                        st == PlaybackState.STATE_SKIPPING_TO_PREVIOUS ||
                        st == PlaybackState.STATE_FAST_FORWARDING ||
                        st == PlaybackState.STATE_REWINDING
                }
            if (playingController != null) {
                return playingController
            }

            // 优先级 2：最近活跃的控制器
            return activeControllers.firstOrNull()
        }
    }

    // 供宿主 recheckSessions 重置签名去重
    fun resetControllerSignatures() {
        lastControllerSignatures = ""
    }

    // 供宿主 recheckSessions / onActiveSessionsChanged 调用
    fun refreshControllers(controllers: List<MediaController>?) {
        updateControllers(controllers)
    }

    // 停止监控：注销全部回调并清空（保持与监听服务解绑一致的清理语义）
    fun stop() {
        synchronized(activeControllers) {
            controllerCallbacks.forEach { (controller, callback) ->
                try {
                    controller.unregisterCallback(callback)
                } catch (e: Exception) {
                    // 忽略
                }
            }
            controllerCallbacks.clear()
            activeControllers.clear()
        }
    }
}
