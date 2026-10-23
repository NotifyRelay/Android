package com.xzyht.notifyrelay.feature.device.service.contract

import com.xzyht.notifyrelay.feature.device.model.PendingPairing
import com.xzyht.notifyrelay.feature.device.service.callback.HandshakeRequestHandler

/**
 * 设备配对契约：配对对话框与设备列表对话框所需的配对状态与操作。
 */
interface DevicePairingPort {
    /** 本机 uuid。 */
    val localUuid: String

    /** 本机长期 ECDH 公钥（Base64 编码的 65 字节未压缩点）。 */
    val localPublicKey: String

    /** 待处理的配对请求。 */
    var pendingPairing: PendingPairing?

    /** 配对请求 UI 处理器（收到 PAIRING_INIT 时通过它弹出配对码框）。 */
    var handshakeRequestHandler: HandshakeRequestHandler?

    /** 取消当前挂起的配对。 */
    fun cancelPendingPairing()

    /** 被用户拒绝的设备集合。 */
    fun getRejectedDevices(): Set<String>

    /** 从已拒绝集合中恢复指定设备，返回移除后的完整已拒绝集合。 */
    fun restoreRejectedDevices(uuids: Collection<String>): Set<String>

    /** 移除已认证设备；[deleteHistory] 为 true 时同时删除其通知历史。 */
    suspend fun removeAuthenticatedDevice(
        uuid: String,
        deleteHistory: Boolean = false,
    ): Boolean

    /** 异步刷新设备快照。 */
    fun triggerDeviceListRefresh()
}

/**
 * 设备管理契约：设备列表页同时需要查询与配对操作，收敛为单一参数。
 */
interface DeviceAdminPort :
    DeviceQueryPort,
    DevicePairingPort
