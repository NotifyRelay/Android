package com.xzyht.notifyrelay.sync

import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerUtil
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import com.xzyht.notifyrelay.nativecore.NativeCore
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

        // 记录发现的设备到 Rust core
        val ctx = deviceManager.rustContextInternal
        if (ctx != null) {
            try {
                NativeCore.recordDiscoveredDevice(ctx, uuid, info.displayName, info.ip, info.port.toShort(), info.batteryLevel, info.deviceType)
            } catch (_: Exception) {}
        }

        val isAuthed = synchronized(deviceManager.authenticatedDevices) {
            deviceManager.authenticatedDevices.containsKey(uuid)
        }

        synchronized(deviceManager.deviceLastSeenInternal) {
            deviceManager.deviceLastSeenInternal[uuid] = System.currentTimeMillis()
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

            synchronized(deviceManager.heartbeatedDevicesInternal) {
                deviceManager.heartbeatedDevicesInternal.add(uuid)
            }

            if (needSave) {
                deviceManager.saveAuthedDevicesInternal()
            }

            if (info.ip != "0.0.0.0" && info.ip.isNotBlank()) {
                synchronized(deviceManager.deviceInfoCacheInternal) {
                    deviceManager.deviceInfoCacheInternal[uuid] = device
                }
            }
            DeviceConnectionManagerUtil.updateGlobalDeviceName(uuid, info.displayName)

            deviceManager.coroutineScopeInternal.launch {
                deviceManager.updateDeviceListInternal()
            }
        } else {
            if (info.ip != "0.0.0.0" && info.ip.isNotBlank()) {
                synchronized(deviceManager.deviceInfoCacheInternal) {
                    deviceManager.deviceInfoCacheInternal[uuid] = device
                }
            }
            DeviceConnectionManagerUtil.updateGlobalDeviceName(uuid, info.displayName)

            deviceManager.coroutineScopeInternal.launch {
                deviceManager.updateDeviceListInternal()
            }
        }
    }
}
