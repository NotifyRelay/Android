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

    fun parseHeartbeatPayload(msg: String, ip: String, defaultPort: Int): HeartbeatInfo? {
        val parts = msg.split(":")
        if (parts.size < 5) return null

        val uuid = parts[0]
        val rawDisplay = parts[1]
        val portStr = parts[2]
        val batteryStr = parts[3]
        val deviceType = parts[4]

        if (uuid.isEmpty()) return null

        val port = portStr.toIntOrNull() ?: defaultPort
        val displayName = try {
            decodeDisplayName(rawDisplay)
        } catch (_: Exception) {
            rawDisplay
        }

        var batteryLevel = 0
        var isCharging = false
        try {
            if (batteryStr.isNotEmpty()) {
                val chargeSign = batteryStr[0]
                isCharging = chargeSign == '+'
                val batteryPart = batteryStr.substring(1)
                batteryLevel = batteryPart.toIntOrNull()?.coerceIn(0, 100) ?: 0
            }
        } catch (_: Exception) {}

        return HeartbeatInfo(uuid, displayName, port, batteryLevel, isCharging, deviceType, ip)
    }

    private fun decodeDisplayName(encoded: String): String {
        return try {
            val decoded = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
            String(decoded, Charsets.UTF_8)
        } catch (_: Exception) {
            encoded
        }
    }

    fun processHeartbeat(info: HeartbeatInfo, deviceManager: DeviceConnectionManager) {
        val uuid = info.uuid
        if (uuid == deviceManager.uuid) return

        val isAuthed = synchronized(deviceManager.authenticatedDevices) {
            deviceManager.authenticatedDevices.containsKey(uuid)
        }

        deviceManager.deviceLastSeenInternal[uuid] = System.currentTimeMillis()

        val device = DeviceInfo(uuid, info.displayName, info.ip, info.port, info.batteryLevel, if (info.isCharging) '1' else '0')

        if (isAuthed) {
            var needSave = false

            synchronized(deviceManager.authenticatedDevices) {
                val auth = deviceManager.authenticatedDevices[uuid]
                if (auth != null) {
                    val needsUpdate = auth.displayName != info.displayName ||
                            auth.lastIp != info.ip ||
                            auth.deviceType != info.deviceType

                    if (needsUpdate) {
                        deviceManager.authenticatedDevices[uuid] = auth.copy(
                            displayName = info.displayName,
                            lastIp = info.ip,
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

            synchronized(deviceManager.deviceInfoCacheInternal) {
                deviceManager.deviceInfoCacheInternal[uuid] = device
            }
            DeviceConnectionManagerUtil.updateGlobalDeviceName(uuid, info.displayName)

            deviceManager.coroutineScopeInternal.launch {
                deviceManager.updateDeviceListInternal()
            }
        } else {
            synchronized(deviceManager.deviceInfoCacheInternal) {
                deviceManager.deviceInfoCacheInternal[uuid] = device
            }
            DeviceConnectionManagerUtil.updateGlobalDeviceName(uuid, info.displayName)

            deviceManager.coroutineScopeInternal.launch {
                deviceManager.updateDeviceListInternal()
            }
        }
    }
}