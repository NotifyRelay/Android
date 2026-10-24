package com.xzyht.notifyrelay.feature.device.service.callback

import com.xzyht.notifyrelay.core.ffi.CoreCallbacks
import com.xzyht.notifyrelay.nativecore.NativeCore

/**
 * [CoreCallbacks] 的装配实现。
 *
 * 把 UniFFI 回调按类别分派给既有处理器：
 * - [onPairing] → [PairingCallbackHandler]
 * - [onData] → [DataCallbackHandler]
 * - 设备事件 → [DeviceEventCallbackHandler]
 * - 音频/日志 → [NativeCore] 的注册入口
 *
 * 回调运行在 core 的线程上（JNA 附加线程、心跳线程或网络线程），
 * 不得在其中同步调用设备快照接口。
 */
class CoreCallbacksAdapter(
    private val host: DeviceCallbackHost,
) : CoreCallbacks {
    private val pairing = PairingCallbackHandler(host)
    private val data = DataCallbackHandler(host)
    private val events = DeviceEventCallbackHandler(host)

    override fun onPairing(
        deviceUuid: String,
        messageType: String,
        data: String,
        intValue: Int,
        extra: String,
    ) {
        pairing.handle(deviceUuid, messageType, data, intValue, extra)
    }

    override fun onData(
        deviceUuid: String,
        messageType: String,
        plaintext: String,
    ) {
        data.handle(deviceUuid, messageType, plaintext)
    }

    override fun onStateQuery(
        deviceUuid: String,
        featureId: String,
        isMedia: Boolean,
    ): Int = events.handleStateQuery(deviceUuid, featureId, isMedia)

    override fun onDeviceTimeout(deviceUuid: String) {
        events.handleTimeout(deviceUuid)
    }

    override fun onDeviceConnected(
        deviceUuid: String,
        ip: String,
    ) {
        events.handleConnected(deviceUuid, ip)
    }

    override fun onDeviceDisconnected(deviceUuid: String) {
        events.handleDisconnected(deviceUuid)
    }

    override fun onTcpError(error: String) {
        events.handleTcpError(error)
    }

    override fun onDeviceDiscovered(
        deviceUuid: String,
        nameB64: String,
        port: UShort,
        battery: Int,
        deviceType: String,
        ip: String,
    ) {
        events.handleDiscovered(deviceUuid, nameB64, port.toInt(), battery, deviceType, ip)
    }

    override fun onAudioData(
        deviceUuid: String,
        pcm: ByteArray,
        sampleRate: Int,
        channels: Int,
    ) {
        NativeCore.dispatchAudioData(pcm, sampleRate, channels)
    }

    override fun onAudioEvent(
        deviceUuid: String,
        event: String,
        data: String,
    ) {
        NativeCore.dispatchLog(4, "音频事件: $event, 错误: $data")
    }

    override fun onLog(
        level: Int,
        message: String,
    ) {
        NativeCore.dispatchLog(level, message)
    }
}
