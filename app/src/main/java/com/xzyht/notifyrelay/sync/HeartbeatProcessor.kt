package com.xzyht.notifyrelay.sync

import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerUtil
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import kotlinx.coroutines.launch
import notifyrelay.base.util.Logger

object HeartbeatProcessor {

    data class HeartbeatInfo(
        val uuid: String,
        val displayName: String,
        val port: Int,
        val batteryLevel: Int,
        val isCharging: Boolean,
        val deviceType: String,
        val ip: String
    )

    fun processHeartbeat(info: HeartbeatInfo, deviceManager: DeviceConnectionManager) {
        val uuid = info.uuid
        if (uuid == deviceManager.uuid) return

        // 运行时状态（lastSeen/在线判定）已迁移至 Rust DeviceRegistry，此处仅做：
        // 1. 已认证设备的 Room 持久化更新（displayName/lastIp/deviceType）
        // 2. 缓存回填 + 触发 refreshDevicesFromRust 消费 Rust 状态快照

        val isAuthed = synchronized(deviceManager.authenticatedDevices) {
            deviceManager.authenticatedDevices.containsKey(uuid)
        }

        val device = DeviceInfo(uuid, info.displayName, info.ip, info.port, info.batteryLevel, if (info.isCharging) '1' else '0')

        if (isAuthed) {
            var needSave = false

            synchronized(deviceManager.authenticatedDevices) {
                val auth = deviceManager.authenticatedDevices[uuid]
                if (auth != null) {
                    val effectiveIp = info.ip.takeUnless { it == "0.0.0.0" || it.isBlank() }
                    val needsUpdate = auth.displayName != info.displayName ||
                            (effectiveIp != null && auth.lastIp != effectiveIp) ||
                            auth.deviceType != info.deviceType

                    if (needsUpdate) {
                        deviceManager.authenticatedDevices[uuid] = auth.copy(
                            displayName = info.displayName,
                            lastIp = effectiveIp ?: auth.lastIp,
                            deviceType = info.deviceType
                        )
                        needSave = true
                    }
                }
            }

            if (needSave) {
                deviceManager.saveAuthedDevicesInternal()
            }

            synchronized(deviceManager.deviceInfoCacheInternal) {
                val existing = deviceManager.deviceInfoCacheInternal[uuid]
                val effectiveIp = info.ip.takeUnless { it == "0.0.0.0" || it.isBlank() } ?: existing?.ip ?: info.ip
                // 未知电量（超出 [-100,100]）沿用缓存旧值，不覆盖已显示的电量/充电状态
                val batteryUnknown = kotlin.math.abs(info.batteryLevel) > 100
                val batteryLevel = if (batteryUnknown) (existing?.batteryLevel ?: -1) else kotlin.math.abs(info.batteryLevel)
                val chargingStatus = if (batteryUnknown) (existing?.chargingStatus ?: '0') else if (info.isCharging) '1' else '0'
                deviceManager.deviceInfoCacheInternal[uuid] = DeviceInfo(uuid, info.displayName, effectiveIp, info.port, batteryLevel, chargingStatus)
            }
            DeviceConnectionManagerUtil.updateGlobalDeviceName(uuid, info.displayName)

            deviceManager.coroutineScopeInternal.launch {
                deviceManager.updateDeviceListInternal()
            }
        } else {
            synchronized(deviceManager.deviceInfoCacheInternal) {
                val existing = deviceManager.deviceInfoCacheInternal[uuid]
                val effectiveIp = info.ip.takeUnless { it == "0.0.0.0" || it.isBlank() } ?: existing?.ip ?: info.ip
                // 未知电量（超出 [-100,100]）沿用缓存旧值，不覆盖已显示的电量/充电状态
                val batteryUnknown = kotlin.math.abs(info.batteryLevel) > 100
                val batteryLevel = if (batteryUnknown) (existing?.batteryLevel ?: -1) else kotlin.math.abs(info.batteryLevel)
                val chargingStatus = if (batteryUnknown) (existing?.chargingStatus ?: '0') else if (info.isCharging) '1' else '0'
                deviceManager.deviceInfoCacheInternal[uuid] = DeviceInfo(uuid, info.displayName, effectiveIp, info.port, batteryLevel, chargingStatus)
            }
            DeviceConnectionManagerUtil.updateGlobalDeviceName(uuid, info.displayName)

            deviceManager.coroutineScopeInternal.launch {
                deviceManager.updateDeviceListInternal()
            }
        }
    }
}
