package com.xzyht.notifyrelay.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
import notifyrelay.base.log.Logger
import notifyrelay.data.Storage
import notifyrelay.data.config.ScrcpyDefaults
import notifyrelay.shared.feature.device.model.DeviceInfo

/**
 * 音频转发操作的承载。
 *
 * 界面通过本 ViewModel 触发发送/接收/停止，不再直接访问设备端口与服务。
 */
class AudioRelayViewModel : ViewModel() {
    /** 发送本端音频到对端；返回需要提示的消息。 */
    fun requestSendAudio(
        context: Context,
        device: DeviceInfo,
    ): String =
        try {
            val relayMode = Storage.getInt("audio_relay_mode", 0)
            if (relayMode == 1) {
                val audioRelay = DeviceConnectionManagerSingleton.getAudioRelay(context)
                audioRelay.requestSendTo(device.ip, device.displayName, device.uuid)
                "已请求${device.displayName}转发音频"
            } else {
                val deviceManager = DeviceConnectionManagerSingleton.getDeviceSend(context)
                if (deviceManager.requestAudioForwarding(device)) {
                    "已请求${device.displayName}转发音频"
                } else {
                    "请求发送失败"
                }
            }
        } catch (e: Exception) {
            Logger.e("NotifyRelay", "请求音频转发异常", e)
            "请求发送异常: ${e.message}"
        }

    /** 播放对端音频；返回需要提示的消息。 */
    fun requestReceiveAudio(
        context: Context,
        device: DeviceInfo,
    ): String =
        try {
            val relayMode = Storage.getInt("audio_relay_mode", 0)
            if (relayMode == 1) {
                DeviceConnectionManagerSingleton
                    .getAudioRelay(context)
                    .startReceive(device.uuid, 48000, 2)
                "已启动中继音频接收"
            } else {
                val ok =
                    io.github.miuzarte.scrcpyforandroid.services.AudioForwardingService.startAudioForwarding(
                        context,
                        device.ip,
                        ScrcpyDefaults.ADB_PORT,
                        device.displayName,
                    )
                if (ok) "正在连接${device.displayName}..." else "启动失败，可能已有转发在进行中"
            }
        } catch (e: Exception) {
            Logger.e("NotifyRelay", "播放对端音频异常", e)
            "启动异常: ${e.message}"
        }

    /** 停止音频转发。 */
    fun stopAudio(context: Context) {
        io.github.miuzarte.scrcpyforandroid.services.AudioForwardingService
            .stopAudioForwarding(context)
        DeviceConnectionManagerSingleton.getAudioRelay(context).stop()
    }
}
