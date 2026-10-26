package com.xzyht.notifyrelay.feature.device.service.state

import android.content.Context
import notifyrelay.data.Storage
import notifyrelay.data.config.ScrcpyPreferenceKeys
import notifyrelay.data.model.OnlineDeviceInfo
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.device.model.DeviceSnapshot

/**
 * 「在线且已配对设备」列表的持久化缓存，供 scrcpy 投屏等外部能力读取。
 *
 * 该缓存是设备快照的派生产物，由 [DeviceSnapshotStore] 在每次刷新后调用；
 * 仅在内容实际变化时写 SP 并刷新动态快捷方式，避免频繁 IO 与快捷方式重建。
 */
class OnlineDevicesCache(
    private val context: Context,
) {
    private var lastCacheJson: String? = null

    fun update(
        deviceMap: Map<String, Pair<DeviceInfo, Boolean>>,
        addressable: List<DeviceSnapshot>,
    ) {
        try {
            val onlineDevices =
                addressable.mapNotNull { snapshot ->
                    val info = deviceMap[snapshot.uuid]?.first ?: return@mapNotNull null
                    OnlineDeviceInfo(
                        uuid = info.uuid,
                        displayName = info.displayName,
                        ip = info.ip,
                        port = info.port,
                        deviceType = snapshot.deviceType,
                    )
                }
            val json =
                com.google.gson
                    .Gson()
                    .toJson(onlineDevices)

            // 只有当内容实际变化时才执行存储和快捷方式更新
            if (json == lastCacheJson) return
            Storage.putString(
                ScrcpyPreferenceKeys.ONLINE_DEVICES_CACHE,
                json,
                Storage.PrefsType.SCRCPY,
            )
            try {
                io.github.miuzarte.scrcpyforandroid.services.DynamicShortcutManager
                    .updateShortcuts(context)
            } catch (_: Exception) {
            }
            lastCacheJson = json
        } catch (_: Exception) {
        }
    }
}
