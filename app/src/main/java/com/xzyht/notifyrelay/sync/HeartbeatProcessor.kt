package com.xzyht.notifyrelay.sync

import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerUtil
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import com.xzyht.notifyrelay.nativecore.NativeCore
import kotlinx.coroutines.launch
import notifyrelay.base.util.Logger
import org.json.JSONObject

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
        val json = NativeCore.parseHeartbeatJson(msg) ?: return null
        return parseHeartbeatJson(json, ip, defaultPort)
    }

    internal fun parseHeartbeatTcpPayload(msg: String, ip: String, defaultPort: Int): HeartbeatInfo? {
        val json = NativeCore.parseHeartbeatTcpJson(msg) ?: return null
        return parseHeartbeatJson(json, ip, defaultPort)
    }

    private fun parseHeartbeatJson(jsonStr: String, ip: String, defaultPort: Int): HeartbeatInfo? {
        return try {
            val json = JSONObject(jsonStr)
            val uuid = json.optString("uuid", "")
            if (uuid.isEmpty()) return null
            val rawDisplay = json.optString("name_b64", "")
            val port = json.optInt("port", defaultPort)
            val battery = json.optInt("battery", 0)
            val deviceType = json.optString("device_type", "unknown")
            val displayName = try {
                val decoded = android.util.Base64.decode(rawDisplay, android.util.Base64.NO_WRAP)
                String(decoded, Charsets.UTF_8)
            } catch (_: Exception) {
                rawDisplay
            }
            HeartbeatInfo(uuid, displayName, port, kotlin.math.abs(battery), battery >= 0, deviceType, ip)
        } catch (_: Exception) {
            null
        }
    }

    fun processHeartbeat(info: HeartbeatInfo, deviceManager: DeviceConnectionManager) {
        val uuid = info.uuid
        if (uuid == deviceManager.uuid) return

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