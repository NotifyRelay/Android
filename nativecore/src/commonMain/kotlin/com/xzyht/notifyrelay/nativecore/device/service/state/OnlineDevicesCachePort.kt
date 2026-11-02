package com.xzyht.notifyrelay.nativecore.device.service.state

import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.device.model.DeviceSnapshot

/**
 * 在线设备缓存的写入端口。
 *
 * 缓存实现含平台存储依赖，位于平台侧；本端口只暴露快照刷新时所需的写入能力。
 */
interface OnlineDevicesCachePort {
    /**
     * 用最新快照更新缓存。
     *
     * @param devices 在线状态映射
     * @param addressable 在线且已配对、且 IP 可连接的设备快照
     */
    fun update(
        devices: Map<String, Pair<DeviceInfo, Boolean>>,
        addressable: List<DeviceSnapshot>,
    )

    /** 缓存内容变化后的平台回调（如刷新动态快捷方式）。 */
    fun onCacheUpdated()
}
