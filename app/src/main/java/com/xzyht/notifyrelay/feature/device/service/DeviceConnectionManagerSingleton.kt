package com.xzyht.notifyrelay.feature.device.service

import android.content.Context
import com.xzyht.notifyrelay.feature.device.service.contract.AudioRelayPort
import com.xzyht.notifyrelay.feature.device.service.contract.DeviceAdminPort
import com.xzyht.notifyrelay.feature.device.service.contract.DeviceChatPort
import com.xzyht.notifyrelay.feature.device.service.contract.DeviceDiscoveryPort
import com.xzyht.notifyrelay.feature.device.service.contract.DeviceMessagePort
import com.xzyht.notifyrelay.feature.device.service.contract.DeviceQueryPort
import com.xzyht.notifyrelay.feature.device.service.contract.DeviceSendPort
import com.xzyht.notifyrelay.feature.device.service.contract.HandshakeWaiterPort
import com.xzyht.notifyrelay.feature.device.service.contract.NotificationDataPort
import com.xzyht.notifyrelay.feature.device.service.statequery.StateQueryResponder

/**
 * 设备域的装配入口（组合根）。
 *
 * 只负责构造并持有 `DeviceConnectionManager`，对外按端口暴露能力；
 * 业务侧依赖返回的端口类型，不接触具体实现。
 */
object DeviceConnectionManagerSingleton {
    @Volatile
    private var sharedDeviceManager: DeviceConnectionManager? = null

    /**
     * 设备查询端口（只读设备视图）。
     */
    fun getDeviceQuery(context: Context): DeviceQueryPort = shared(context)

    /**
     * 设备发现端口（发现开关 + 在线设备流）。
     */
    fun getDeviceDiscovery(context: Context): DeviceDiscoveryPort = shared(context)

    /**
     * 设备管理端口（设备列表页：查询 + 配对操作）。
     */
    fun getDeviceAdmin(context: Context): DeviceAdminPort = shared(context)

    /**
     * 消息发送端口（通知/聊天/媒体/超级岛消息链路）。
     */
    fun getDeviceMessage(context: Context): DeviceMessagePort = shared(context)

    /**
     * 聊天端口（消息发送 + 入站通知订阅）。
     */
    fun getDeviceChat(context: Context): DeviceChatPort = shared(context)

    /**
     * 通知数据订阅端口。
     */
    fun getNotificationData(context: Context): NotificationDataPort = shared(context)

    /**
     * 设备发送端口。
     */
    fun getDeviceSend(context: Context): DeviceSendPort = shared(context)

    /**
     * 音频中继端口。
     */
    fun getAudioRelay(context: Context): AudioRelayPort = shared(context).audioRelay

    /**
     * 握手结果等待器登记表。
     */
    fun getHandshakeWaiters(context: Context): HandshakeWaiterPort = shared(context).handshakeWaiters

    /**
     * Rust 心跳「状态查询」响应器（超级岛/媒体会话）。
     */
    fun getStateQueryResponder(context: Context): StateQueryResponder = shared(context).stateQueryResponder

    /**
     * 清除实例（仅在测试时使用）。
     */
    fun clearDeviceManager() {
        sharedDeviceManager = null
    }

    private fun shared(context: Context): DeviceConnectionManager =
        sharedDeviceManager ?: synchronized(this) {
            sharedDeviceManager ?: DeviceConnectionManager(context.applicationContext).also { sharedDeviceManager = it }
        }
}
