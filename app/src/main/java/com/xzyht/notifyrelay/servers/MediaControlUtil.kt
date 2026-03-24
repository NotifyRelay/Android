package com.xzyht.notifyrelay.servers

import android.app.PendingIntent
import android.content.Context
import android.media.session.PlaybackState
import notifyrelay.base.util.Logger

object MediaControlUtil {

    /**
     * 使用 MediaSession API 触发播放/暂停
     */
    fun playPause() {
        try {
            val mediaSessionMonitor = MediaSessionMonitorService.instance
            val primaryController = mediaSessionMonitor?.getPrimaryController()
            if (primaryController != null) {
                val playbackState = primaryController.playbackState
                if (playbackState != null) {
                    val actions = playbackState.actions
                    if (actions and PlaybackState.ACTION_PLAY != 0L && playbackState.state == PlaybackState.STATE_PAUSED) {
                        primaryController.transportControls.play()
                        Logger.i("MediaControlUtil", "播放/暂停: 触发播放")
                        return
                    } else if (actions and PlaybackState.ACTION_PAUSE != 0L && playbackState.state == PlaybackState.STATE_PLAYING) {
                        primaryController.transportControls.pause()
                        Logger.i("MediaControlUtil", "播放/暂停: 触发暂停")
                        return
                    }
                }
            }
            Logger.w("MediaControlUtil", "playPause: 未找到媒体会话或不支持该操作")
        } catch (e: Exception) {
            Logger.e("MediaControlUtil", "使用 MediaSession API 触发播放/暂停失败", e)
        }
    }

    /**
     * 使用 MediaSession API 触发下一首
     */
    fun next() {
        try {
            val mediaSessionMonitor = MediaSessionMonitorService.instance
            val primaryController = mediaSessionMonitor?.getPrimaryController()
            if (primaryController != null) {
                val playbackState = primaryController.playbackState
                if (playbackState != null && (playbackState.actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L)) {
                    primaryController.transportControls.skipToNext()
                    Logger.i("MediaControlUtil", "下一首: 触发成功")
                    return
                }
            }
            Logger.w("MediaControlUtil", "next: 未找到媒体会话或不支持该操作")
        } catch (e: Exception) {
            Logger.e("MediaControlUtil", "使用 MediaSession API 触发下一首失败", e)
        }
    }


    /**
     * 使用 MediaSession API 触发上一首
     */
    fun previous() {
        try {
            val mediaSessionMonitor = MediaSessionMonitorService.instance
            val primaryController = mediaSessionMonitor?.getPrimaryController()
            if (primaryController != null) {
                val playbackState = primaryController.playbackState
                if (playbackState != null && (playbackState.actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L)) {
                    primaryController.transportControls.skipToPrevious()
                    Logger.i("MediaControlUtil", "上一首: 触发成功")
                    return
                }
            }
            Logger.w("MediaControlUtil", "previous: 未找到媒体会话或不支持该操作")
        } catch (e: Exception) {
            Logger.e("MediaControlUtil", "使用 MediaSession API 触发上一首失败", e)
        }
    }



    /**
     * 使用 MediaSession API 触发 seekTo
     */
    fun seekTo(position: Long) {
        try {
            val mediaSessionMonitor = MediaSessionMonitorService.instance
            val primaryController = mediaSessionMonitor?.getPrimaryController()
            if (primaryController != null) {
                val playbackState = primaryController.playbackState
                if (playbackState != null && (playbackState.actions and PlaybackState.ACTION_SEEK_TO != 0L)) {
                    primaryController.transportControls.seekTo(position)
                    Logger.i("MediaControlUtil", "seekTo: 触发成功，位置: $position")
                    return
                }
            }
        } catch (e: Exception) {
            Logger.e("MediaControlUtil", "使用 MediaSession API 触发 seekTo 失败", e)
        }
        Logger.w("MediaControlUtil", "seekTo: 未找到媒体会话或不支持该操作")
    }
    
}