package notifyrelay.domain.feature.device.service.contract

import kotlinx.coroutines.CompletableDeferred
import notifyrelay.domain.feature.device.model.AuthInfo
import notifyrelay.domain.feature.device.model.DeviceInfo

/**
 * 握手结果等待契约：UI 挂起等待远端配对/连接响应。
 */
interface HandshakeWaiterPort {
    /** 注册等待握手结果（同一 uuid 的旧等待器会被取消）。 */
    fun register(uuid: String): CompletableDeferred<Boolean>

    /** 读取该 uuid 最近一次失败原因。 */
    fun failureReason(uuid: String): String?

    /** 按 Deferred 实例清理等待器，防止迟到请求完成或移除其他等待器。 */
    fun cancel(
        uuid: String,
        deferred: CompletableDeferred<Boolean>,
    )
}

/**
 * 音频中继契约：UI 发起/停止投送与接收。
 */
interface AudioRelayControlPort {
    /** 屏幕捕获授权请求回调（由 UI 注入）。 */
    var onRequestMediaProjection: (() -> Unit)?

    /** 发起向指定设备的音频投送：先请求屏幕捕获授权，授权后由 [startPendingSend] 启动。 */
    fun requestSendTo(
        deviceIp: String,
        deviceName: String,
        remoteUuid: String,
    )

    /** 屏幕捕获授权成功后调用，启动挂起的投送会话。 */
    fun startPendingSend()

    /** 启动接收侧播放并展示前台通知。 */
    fun startReceive(
        remoteUuid: String,
        sampleRate: Int,
        channels: Int,
    )

    /** 主动停止中继。 */
    fun stop()

    /** 停止发送侧采集。 */
    fun stopSendCapture()

    /** 停止并清空屏幕投影。 */
    fun cleanupMediaProjection()
}

/**
 * 设备通知数据订阅契约：UI 订阅入站通知数据。
 */
interface NotificationDataPort {
    /** 登记通知数据接收回调。 */
    fun registerOnNotificationDataReceived(callback: (String) -> Unit)

    /** 注销通知数据接收回调。 */
    fun unregisterOnNotificationDataReceived(callback: (String) -> Unit)
}

/**
 * 设备发送契约：UI 向指定设备发起协议请求。
 */
interface DeviceSendPort {
    /** 向指定设备请求音频转发，返回是否成功发送。 */
    fun requestAudioForwarding(device: DeviceInfo): Boolean
}

/**
 * 已认证设备表只读快照契约：供发送链路判定目标设备是否已认证。
 *
 * 快照由实现方在同一把锁内复制，调用方不再直接持有内部可变表。
 */
interface DeviceAuthTablePort {
    /** 已认证设备表快照（key=uuid）。 */
    fun authenticatedSnapshot(): Map<String, AuthInfo>

    /** 本机 uuid。 */
    val localUuid: String
}

/**
 * 设备发现契约：通知监听服务所需的发现开关与在线设备流。
 */
interface DeviceDiscoveryPort : DeviceQueryPort {
    /** 是否已启用设备发现。 */
    var discoveryEnabled: Boolean

    /** 启动局域网发现。 */
    fun startDiscovery()

    /** 停止局域网发现。 */
    fun stopDiscovery()
}

/**
 * 消息发送契约：通知/聊天/媒体/超级岛消息链路所需的设备视图与认证状态。
 */
interface DeviceMessagePort :
    DeviceQueryPort,
    DeviceAuthTablePort

/**
 * 聊天测试契约：聊天页同时需要消息发送与入站通知订阅。
 */
interface DeviceChatPort :
    DeviceMessagePort,
    NotificationDataPort
