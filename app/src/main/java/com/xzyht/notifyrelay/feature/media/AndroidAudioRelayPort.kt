package com.xzyht.notifyrelay.feature.media

import android.content.Context
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
import notifyrelay.base.log.Logger
import notifyrelay.data.Storage
import notifyrelay.data.config.ScrcpyDefaults
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.media.AudioOperationResult
import notifyrelay.domain.feature.media.AudioRelayPort

/** 以现有设备端口与 scrcpy 服务实现 [AudioRelayPort]。 */
class AndroidAudioRelayPort(
    private val context: Context,
) : AudioRelayPort {
    override fun relayMode(): Int = Storage.getInt("audio_relay_mode", 0)

    override fun requestSendAudio(device: DeviceInfo): AudioOperationResult =
        try {
            if (relayMode() == 1) {
                DeviceConnectionManagerSingleton
                    .getAudioRelay(context)
                    .requestSendTo(device.ip, device.displayName, device.uuid)
                AudioOperationResult.Success("已请求" + device.displayName + "转发音频")
            } else {
                val deviceManager = DeviceConnectionManagerSingleton.getDeviceSend(context)
                if (deviceManager.requestAudioForwarding(device)) {
                    AudioOperationResult.Success("已请求" + device.displayName + "转发音频")
                } else {
                    AudioOperationResult.Failure("请求发送失败")
                }
            }
        } catch (e: Exception) {
            Logger.e("NotifyRelay", "请求音频转发异常", e)
            AudioOperationResult.Failure("请求发送异常: " + (e.message ?: ""))
        }

    override fun requestReceiveAudio(device: DeviceInfo): AudioOperationResult =
        try {
            if (relayMode() == 1) {
                DeviceConnectionManagerSingleton
                    .getAudioRelay(context)
                    .startReceive(device.uuid, 48000, 2)
                AudioOperationResult.Success("已启动中继音频接收")
            } else {
                val ok =
                    io.github.miuzarte.scrcpyforandroid.services.AudioForwardingService.startAudioForwarding(
                        context,
                        device.ip,
                        ScrcpyDefaults.ADB_PORT,
                        device.displayName,
                    )
                if (ok) {
                    AudioOperationResult.Success("正在连接" + device.displayName + "...")
                } else {
                    AudioOperationResult.Failure("启动失败，可能已有转发在进行中")
                }
            }
        } catch (e: Exception) {
            Logger.e("NotifyRelay", "播放对端音频异常", e)
            AudioOperationResult.Failure("启动异常: " + (e.message ?: ""))
        }

    override fun stopAudio() {
        io.github.miuzarte.scrcpyforandroid.services.AudioForwardingService
            .stopAudioForwarding(context)
        DeviceConnectionManagerSingleton.getAudioRelay(context).stop()
    }
}
