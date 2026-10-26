package com.xzyht.notifyrelay.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
import com.xzyht.notifyrelay.feature.media.MediaControlUtil
import com.xzyht.notifyrelay.feature.media.RemoteMediaSessionManager
import com.xzyht.notifyrelay.sync.ProtocolSender
import notifyrelay.domain.feature.device.model.DeviceInfo

/**
 * 远端媒体控制的承载。
 *
 * 界面通过本 ViewModel 触发播放控制，不再直接访问 [RemoteMediaSessionManager] 与 [DeviceConnectionManagerSingleton]。
 */
class MediaControlViewModel : ViewModel() {
    fun onPlayPause(context: Context) {
        RemoteMediaSessionManager.onPlayPause(context, DeviceConnectionManagerSingleton.getDeviceMessage(context))
    }

    fun onPrevious(context: Context) {
        RemoteMediaSessionManager.onPrevious(context, DeviceConnectionManagerSingleton.getDeviceMessage(context))
    }

    fun onNext(context: Context) {
        RemoteMediaSessionManager.onNext(context, DeviceConnectionManagerSingleton.getDeviceMessage(context))
    }

    fun currentSession() = RemoteMediaSessionManager.getCurrentSession()

    fun currentDevice(): DeviceInfo? = RemoteMediaSessionManager.getCurrentDevice()

    fun isEnabled(context: Context): Boolean = RemoteMediaSessionManager.isEnabled(context)

    fun receiveMode(context: Context) = RemoteMediaSessionManager.getReceiveMode(context)

    fun setReceiveMode(
        context: Context,
        mode: notifyrelay.domain.feature.media.MediaMessageReceiveMode,
    ) {
        RemoteMediaSessionManager.setReceiveMode(context, mode)
    }

    /** 控制本机播放器：上一首。 */
    fun previousLocal() {
        MediaControlUtil.previous()
    }

    /** 控制本机播放器：播放/暂停。 */
    fun playPauseLocal() {
        MediaControlUtil.playPause()
    }

    /** 控制本机播放器：下一首。 */
    fun nextLocal() {
        MediaControlUtil.next()
    }

    /** 向远端发送媒体指令。 */
    fun sendToRemote(
        context: Context,
        action: String,
    ) {
        val port = DeviceConnectionManagerSingleton.getDeviceMessage(context)
        when (action) {
            "previous" -> RemoteMediaSessionManager.onPrevious(context, port)
            "playPause" -> RemoteMediaSessionManager.onPlayPause(context, port)
            "next" -> RemoteMediaSessionManager.onNext(context, port)
        }
    }

    /** 向远端发送媒体控制指令；返回是否入队成功。 */
    fun sendMediaCommand(
        context: Context,
        device: DeviceInfo,
        action: String,
    ): Boolean {
        val port = DeviceConnectionManagerSingleton.getDeviceMessage(context)
        val raw = "{\"type\":\"MEDIA_CONTROL\",\"action\":\"$action\"}"
        return ProtocolSender.sendEncrypted(port, device, "DATA_MEDIA_CONTROL", raw) == ProtocolSender.EnqueueResult.SUCCESS
    }
}
