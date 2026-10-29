package notifyrelay.desktop.platform

import notifyrelay.data.config.SuperIslandConfigPolicy
import notifyrelay.domain.feature.media.AudioOperationResult
import notifyrelay.domain.feature.media.AudioRelayPort
import notifyrelay.domain.feature.notification.superisland.SpecInjectionMode
import notifyrelay.domain.feature.notification.superisland.SuperIslandConfigPort
import notifyrelay.domain.platform.FormFactorPortRegistry

// 桌面端音频转发的两种模式与 Android 端同构：
// - relayMode=0（scrcpy）：发送＝请对端推流；接收＝本机拉取对端声音；
// - relayMode=1（中继）：发送＝本机 WASAPI 回环采集后推流；接收＝本机 Rust recv 收流并播放。

/**
 * 音频转发端口。
 *
 * 两个方向语义不同（对齐 Android `AudioRelayPort`）：
 * - [requestSendAudio]：请求**对端**把音频推给本机，本机不启进程；
 * - [requestReceiveAudio]：本机主动拉取/接收对端音频并在本机播放。
 */
class DesktopAudioRelayPort(
    /** 当前是否在线可寻址的设备（发送控制报文用）。 */
    private val findDevice: (uuid: String) -> notifyrelay.domain.feature.device.model.DeviceInfo? = { null },
    /** 发送一条 `DATA_MEDIA_CONTROL` 报文；返回是否已交给发送链路。 */
    private val sendControl: (notifyrelay.domain.feature.device.model.DeviceInfo, String) -> Boolean = { _, _ -> false },
) : AudioRelayPort {
    override fun relayMode(): Int = notifyrelay.data.Storage.getInt(KEY_RELAY_MODE, 0)

    /**
     * 发送本机音频到对端。
     *
     * scrcpy 模式：发 `audioRequest`，由对端启动仅音频 scrcpy 连接本机并推流；
     * 中继模式：本机经 WASAPI 回环采集系统声音，写入 Rust 音频流推给对端。
     */
    override fun requestSendAudio(device: notifyrelay.domain.feature.device.model.DeviceInfo): AudioOperationResult {
        if (relayMode() == RELAY_MODE) return startRelaySend(device)
        val sent =
            sendControl(
                device,
                """{"type":"MEDIA_CONTROL","action":"$ACTION_AUDIO_REQUEST"}""",
            )
        return if (sent) {
            AudioOperationResult.Success("已请求" + device.displayName + "转发音频")
        } else {
            AudioOperationResult.Failure("请求发送失败，请确认设备在线")
        }
    }

    /**
     * 接收并播放对端音频。
     *
     * scrcpy 模式：本机启动仅音频 scrcpy 拉取对端声音；
     * 中继模式：本机 Rust recv 通道收流，PCM 经 JavaSound 播放。
     */
    override fun requestReceiveAudio(device: notifyrelay.domain.feature.device.model.DeviceInfo): AudioOperationResult {
        if (relayMode() == RELAY_MODE) return startRelayReceive(device)
        val ok =
            DesktopScrcpyLauncher.launchAudio(
                deviceUuid = device.uuid,
                deviceName = device.displayName,
                deviceIp = device.ip,
            )
        if (!ok) return AudioOperationResult.Failure("启动失败，可能已有音频转发在进行中")
        activeDevices[device.uuid] = device
        return AudioOperationResult.Success("正在连接" + device.displayName + "...")
    }

    override fun stopAudio() {
        // 停止 scrcpy 拉流
        activeDevices.values.forEach { device ->
            DesktopScrcpyLauncher.stopByDeviceUuid(device.uuid, device.displayName, device.ip)
        }
        activeDevices.clear()
        // 停止 Rust 中继收发与播放通道
        stopRelayReceive()
        stopRelaySend()
    }

    /** 正在经 scrcpy 拉流音频的设备：UUID → 设备信息。 */
    private val activeDevices = java.util.concurrent.ConcurrentHashMap<String, notifyrelay.domain.feature.device.model.DeviceInfo>()

    /** 中继模式发送是否在运行。 */
    @Volatile
    private var relaySending = false

    /** 中继模式接收是否在运行。 */
    @Volatile
    private var relayReceiving = false

    /**
     * 中继模式发送：采集本机系统声音并写入 Rust 音频流。
     *
     * 采集在 smtc-rust 的 cpal 桥完成（对默认渲染设备开输入流即为 WASAPI 回环），
     * 每帧 PCM 经 [com.xzyht.notifyrelay.nativecore.NativeCore.audioWriteFrame] 推给对端。
     */
    private fun startRelaySend(device: notifyrelay.domain.feature.device.model.DeviceInfo): AudioOperationResult {
        if (relaySending) return AudioOperationResult.Failure("已有音频转发在进行中")
        if (!com.xzyht.notifyrelay.nativecore.NativeCore
                .isReady()
        ) {
            return AudioOperationResult.Failure("原生核心未就绪")
        }
        // 先启动采集，取得真实采样率与声道数后再开流（Rust 侧音频流配置需与此一致）
        var started = false
        val ok =
            DesktopPcmCapture.start { pcm ->
                if (started) {
                    runCatching {
                        com.xzyht.notifyrelay.nativecore.NativeCore
                            .audioWriteFrame(pcm)
                    }
                }
            }
        if (!ok) return AudioOperationResult.Failure("采集本机声音失败，请确认系统输出设备可用")
        val (rate, channels) = DesktopPcmCapture.format()
        val ret =
            com.xzyht.notifyrelay.nativecore.NativeCore.audioStart(
                "send",
                AUDIO_PORT,
                if (rate > 0) rate else SAMPLE_RATE,
                if (channels > 0) channels else CHANNELS,
                device.uuid,
            )
        if (ret != 0) {
            DesktopPcmCapture.stop()
            return AudioOperationResult.Failure("启动音频发送失败")
        }
        started = true
        relaySending = true
        return AudioOperationResult.Success("已开始向" + device.displayName + "发送本机音频")
    }

    /** 停止中继发送。 */
    private fun stopRelaySend() {
        if (!relaySending) return
        relaySending = false
        DesktopPcmCapture.stop()
        runCatching {
            com.xzyht.notifyrelay.nativecore.NativeCore
                .audioStop()
        }
    }

    /** 中继模式接收：启动 Rust recv 并注册 PCM 回调交给 JavaSound 播放。 */
    private fun startRelayReceive(device: notifyrelay.domain.feature.device.model.DeviceInfo): AudioOperationResult {
        if (relayReceiving) return AudioOperationResult.Failure("已有音频转发在进行中")
        if (!com.xzyht.notifyrelay.nativecore.NativeCore
                .isReady()
        ) {
            return AudioOperationResult.Failure("原生核心未就绪")
        }
        val ret =
            com.xzyht.notifyrelay.nativecore.NativeCore.audioStart(
                "recv",
                AUDIO_PORT,
                SAMPLE_RATE,
                CHANNELS,
                device.uuid,
            )
        if (ret != 0) return AudioOperationResult.Failure("启动音频接收失败")
        if (!DesktopPcmPlayer.open(SAMPLE_RATE, CHANNELS)) {
            com.xzyht.notifyrelay.nativecore.NativeCore
                .audioStop()
            return AudioOperationResult.Failure("本机音频输出设备不可用")
        }
        // Rust 解码后交出 PCM，本机负责播放
        com.xzyht.notifyrelay.nativecore.NativeCore.registerAudioDataCallback { pcm, _, _ ->
            DesktopPcmPlayer.write(pcm, pcm.size)
        }
        relayReceiving = true
        return AudioOperationResult.Success("已启动中继音频接收")
    }

    /** 停止中继接收。 */
    private fun stopRelayReceive() {
        if (!relayReceiving) return
        relayReceiving = false
        runCatching {
            com.xzyht.notifyrelay.nativecore.NativeCore
                .audioStop()
        }
        DesktopPcmPlayer.close()
    }

    private companion object {
        /** 中继模式开关键，与 Android 端同名。 */
        const val KEY_RELAY_MODE = "audio_relay_mode"

        /** 中继模式取值：1 = Rust 音频流通道。 */
        const val RELAY_MODE = 1

        /** 音频流端口，与 Android 端一致。 */
        const val AUDIO_PORT = 23335

        const val SAMPLE_RATE = 48000

        const val CHANNELS = 2

        const val ACTION_AUDIO_REQUEST = "audioRequest"
    }
}

/**
 * 超级岛配置：键与互斥推导委托公共策略。
 *
 * 桌面端非平板、无系统版本判定，故浮窗默认开、列表模式默认关；
 * 展示层未接线，[dismissAllRemote] 与 [switchChannel] 保持空操作。
 */
class DesktopSuperIslandConfigPort : SuperIslandConfigPort {
    private val formFactor get() = FormFactorPortRegistry.current

    override fun isRemoteDisplayEnabled(): Boolean = SuperIslandConfigPolicy.isRemoteDisplayEnabled()

    override fun setRemoteDisplayEnabled(enabled: Boolean) = SuperIslandConfigPolicy.setRemoteDisplayEnabled(enabled)

    override fun isFloatingWindowEnabled(): Boolean = SuperIslandConfigPolicy.isFloatingWindowEnabled(formFactor)

    override fun setFloatingWindowEnabled(enabled: Boolean): Boolean = SuperIslandConfigPolicy.setFloatingWindowEnabled(formFactor, enabled)

    override fun isNotificationListMode(): Boolean = SuperIslandConfigPolicy.isNotificationListMode(formFactor)

    override fun setNotificationListMode(enabled: Boolean): Boolean = SuperIslandConfigPolicy.setNotificationListMode(formFactor, enabled)

    override fun specInjectionMode(): SpecInjectionMode = SuperIslandConfigPolicy.specInjectionMode()

    override fun setSpecInjectionMode(mode: SpecInjectionMode) = SuperIslandConfigPolicy.setSpecInjectionMode(mode)

    override fun dismissAllRemote() = Unit

    override fun switchChannel() = Unit
}
