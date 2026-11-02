package com.xzyht.notifyrelay.nativecore.device.service.state

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import notifyrelay.data.Storage
import notifyrelay.data.config.ScrcpyPreferenceKeys
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.device.model.DeviceSnapshot

/** 在线设备的持久化记录（供 scrcpy 等外部能力读取）。 */
@Serializable
data class OnlineDeviceRecord(
    val uuid: String,
    val displayName: String,
    val ip: String,
    val port: Int,
    val deviceType: String,
)

/**
 * 「在线且已配对设备」列表的持久化缓存。
 *
 * 该缓存是设备快照的派生产物，由 [DeviceSnapshotStore] 在每次刷新后调用；
 * 仅在内容实际变化时写存储，避免频繁 IO。
 * 平台专属副作用（如刷新动态快捷方式）经 [onCacheUpdated] 交给实现方。
 */
open class OnlineDevicesCache : OnlineDevicesCachePort {
    private var lastCacheJson: String? = null

    override fun update(
        deviceMap: Map<String, Pair<DeviceInfo, Boolean>>,
        addressable: List<DeviceSnapshot>,
    ) {
        try {
            val onlineDevices =
                addressable.mapNotNull { snapshot ->
                    val info = deviceMap[snapshot.uuid]?.first ?: return@mapNotNull null
                    OnlineDeviceRecord(
                        uuid = info.uuid,
                        displayName = info.displayName,
                        ip = info.ip,
                        port = info.port,
                        deviceType = snapshot.deviceType,
                    )
                }
            val json = json.encodeToString(onlineDevices)

            // 只有当内容实际变化时才执行存储与平台副作用
            if (json == lastCacheJson) return
            Storage.putString(
                ScrcpyPreferenceKeys.ONLINE_DEVICES_CACHE,
                json,
                Storage.PrefsType.SCRCPY,
            )
            onCacheUpdated()
            lastCacheJson = json
        } catch (_: Exception) {
        }
    }

    /** 平台专属副作用；默认无操作。 */
    override fun onCacheUpdated() = Unit

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
