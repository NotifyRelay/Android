package com.xzyht.notifyrelay.feature.device.service.contract

import com.xzyht.notifyrelay.feature.device.model.AuthInfo
import com.xzyht.notifyrelay.feature.device.model.DeviceInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * 设备只读查询契约：UI 读取设备视图的唯一入口。
 */
interface DeviceQueryPort {
    /** 设备状态流：key=uuid，value=(DeviceInfo, 是否在线)。 */
    val devices: StateFlow<Map<String, Pair<DeviceInfo, Boolean>>>

    /** 已配对设备表（展示元数据来自 core 快照）。 */
    fun getAuthenticatedDevices(): Map<String, AuthInfo>

    /** 已配对设备的 uuid 集合（含离线）。 */
    fun getPairedDeviceUuids(): Set<String>

    /** 在线且已配对的设备数量。 */
    fun getAuthenticatedOnlineCount(): Int

    /** 在线且已配对的设备列表。 */
    fun getAuthenticatedOnlineDevices(): List<DeviceInfo>

    /** 按 uuid 反查设备信息。 */
    fun resolveDeviceInfo(
        uuid: String,
        fallbackIp: String? = null,
        fallbackPort: Int = 23333,
    ): DeviceInfo?
}
