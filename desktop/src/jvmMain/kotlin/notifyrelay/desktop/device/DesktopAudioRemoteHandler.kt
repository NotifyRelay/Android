package notifyrelay.desktop.device

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import notifyrelay.base.log.Logger
import notifyrelay.desktop.DataLayerDesktop
import notifyrelay.desktop.platform.DesktopPcmPlayer

/**
 * 桌面端入站音频转发控制处理。
 *
 * 与 Android `AudioRelayController` 的入站分支同构：
 * - `audioRequest`：对端请求本机把声音推到它那里播放；
 * - `audioResponse`：对端对本机请求的回执；
 * - `audioStart`：对端已开始推送音频，本机启动接收播放；
 * - `audioStop`：对端停止推送，本机清理。
 *
 * 采集本机声音依赖系统级回环采集，桌面端暂缓；因此 `audioRequest` 在 scrcpy 模式下
 * 仅记录并回执拒绝，避免对端长时间等待。
 */
internal object DesktopAudioRemoteHandler {
    private const val TAG = "DesktopAudio"

    /** 中继模式取值：1 = Rust 音频流通道。 */
    private const val RELAY_MODE = 1

    /** 音频流端口，与 Android 端一致。 */
    private const val AUDIO_PORT = 23335

    private const val SAMPLE_RATE = 48000

    private const val CHANNELS = 2

    /** 对端请求本机推送音频。 */
    fun onAudioRequest(root: JsonObject) {
        val relayMode = runCatching { notifyrelay.data.Storage.getInt("audio_relay_mode", 0) }.getOrDefault(0)
        if (relayMode == RELAY_MODE) {
            Logger.w(TAG, "中继模式请求本机推送音频，但桌面端暂不支持采集本机声音")
        } else {
            Logger.w(TAG, "scrcpy 模式下对端请求本机推送音频，桌面端暂不支持采集本机声音")
        }
        // 回执拒绝，避免对端等待超时
        replyAudioResponse(root, accepted = false)
    }

    /** 对端对本机 audioRequest 的回执。 */
    fun onAudioResponse(root: JsonObject) {
        val accepted =
            (root["result"] as? JsonPrimitive)
                ?.content
                ?.equals("accepted", ignoreCase = true) ?: false
        if (!accepted) Logger.w(TAG, "对端拒绝了音频转发请求")
    }

    /** 对端开始推送音频：本机启动 Rust 接收并播放。 */
    fun onAudioStart(
        root: JsonObject,
        senderUuid: String? = null,
    ) {
        if (DesktopPcmPlayer.isRunning) return
        if (!com.xzyht.notifyrelay.nativecore.NativeCore
                .isReady()
        ) {
            Logger.w(TAG, "原生核心未就绪，无法接收音频")
            return
        }
        // 报文中的设备字段在不同实现里为 remoteUuid / fromUuid / deviceUuid，取首个非空
        val remoteUuid =
            listOf("remoteUuid", "fromUuid", "deviceUuid")
                .firstNotNullOfOrNull { key ->
                    (root[key] as? JsonPrimitive)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                }.orEmpty()
        val ret =
            com.xzyht.notifyrelay.nativecore.NativeCore.audioStart(
                "recv",
                AUDIO_PORT,
                SAMPLE_RATE,
                CHANNELS,
                remoteUuid,
            )
        if (ret != 0) {
            Logger.w(TAG, "启动音频接收失败")
            return
        }
        if (!DesktopPcmPlayer.open(SAMPLE_RATE, CHANNELS)) {
            com.xzyht.notifyrelay.nativecore.NativeCore
                .audioStop()
            return
        }
        com.xzyht.notifyrelay.nativecore.NativeCore.registerAudioDataCallback { pcm, _, _ ->
            DesktopPcmPlayer.write(pcm, pcm.size)
        }
        // 对端发起的接收同样发布到系统媒体中心：报文字段优先，其次取报文发送方 uuid
        val sourceDevice =
            DataLayerDesktop.findOnlineDevice(remoteUuid)
                ?: senderUuid?.let { DataLayerDesktop.findOnlineDevice(it) }
        if (sourceDevice == null) {
            Logger.w(TAG, "未找到音频来源设备: remoteUuid=" + remoteUuid + " senderUuid=" + senderUuid)
        } else {
            notifyrelay.desktop.platform.DesktopSystemMediaControl
                .onRelayStarted(sourceDevice)
        }
        Logger.i(TAG, "已开始接收对端音频")
    }

    /** 对端停止推送音频。 */
    fun onAudioStop() {
        // 无论播放器是否已关闭，都要撤回系统媒体中心会话
        notifyrelay.desktop.platform.DesktopSystemMediaControl
            .onRelayStopped()
        if (!DesktopPcmPlayer.isRunning) return
        runCatching {
            com.xzyht.notifyrelay.nativecore.NativeCore
                .audioStop()
        }
        DesktopPcmPlayer.close()
        Logger.i(TAG, "已停止接收对端音频")
    }

    /** 回 audioResponse 给请求方。 */
    private fun replyAudioResponse(
        root: JsonObject,
        accepted: Boolean,
    ) {
        val host = DataLayerDesktop.callbackHost() ?: return
        val fromUuid = (root["fromUuid"] as? JsonPrimitive)?.jsonPrimitive?.content.orEmpty()
        val device = DataLayerDesktop.findOnlineDevice(fromUuid) ?: return
        val result = if (accepted) "accepted" else "rejected"
        runCatching {
            host.sendDataMessage(
                device,
                "DATA_MEDIA_CONTROL",
                """{"type":"MEDIA_CONTROL","action":"audioResponse","result":""" + result + "\"}",
            )
        }
    }
}
