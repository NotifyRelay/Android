package com.xzyht.notifyrelay.sync

import com.sun.jna.Pointer
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerUtil
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import com.xzyht.notifyrelay.nativecore.NativeCore
import com.xzyht.notifyrelay.nativecore.NotifyRelayCore
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

    /**
     * 通过 Rust 回调式解析直接构造 HeartbeatInfo，消除 JSON 中间格式
     */
    fun parseHeartbeatPayload(msg: String, ip: String, defaultPort: Int): HeartbeatInfo? {
        val result = mutableListOf<HeartbeatInfo>()
        val cb = object : NotifyRelayCore.OnHeartbeatWithCb {
            override fun invoke(uuidPtr: Pointer?, nameB64Ptr: Pointer?, port: Short, battery: Int, deviceTypePtr: Pointer?, userData: Pointer?) {
                val uuid = NotifyRelayCore.ptrToString(uuidPtr) ?: return
                val nameB64 = NotifyRelayCore.ptrToString(nameB64Ptr) ?: ""
                val deviceType = NotifyRelayCore.ptrToString(deviceTypePtr) ?: "unknown"
                val displayName = try {
                    val decoded = java.util.Base64.getDecoder().decode(nameB64)
                    String(decoded, Charsets.UTF_8)
                } catch (_: Exception) { nameB64 }
                result.add(HeartbeatInfo(uuid, displayName, port.toInt(), kotlin.math.abs(battery), battery >= 0, deviceType, ip))
            }
        }
        NativeCore.lib.nrc_parse_heartbeat_with_cb(msg, cb, null)
        return result.firstOrNull()
    }

    internal fun parseHeartbeatTcpPayload(msg: String, ip: String, defaultPort: Int): HeartbeatInfo? {
        val result = mutableListOf<HeartbeatInfo>()
        val cb = object : NotifyRelayCore.OnHeartbeatTcpWithCb {
            override fun invoke(uuidPtr: Pointer?, nameB64Ptr: Pointer?, port: Short, battery: Int, deviceTypePtr: Pointer?, ipPtr: Pointer?, userData: Pointer?) {
                val uuid = NotifyRelayCore.ptrToString(uuidPtr) ?: return
                val nameB64 = NotifyRelayCore.ptrToString(nameB64Ptr) ?: ""
                val deviceType = NotifyRelayCore.ptrToString(deviceTypePtr) ?: "unknown"
                val displayName = try {
                    val decoded = java.util.Base64.getDecoder().decode(nameB64)
                    String(decoded, Charsets.UTF_8)
                } catch (_: Exception) { nameB64 }
                result.add(HeartbeatInfo(uuid, displayName, port.toInt(), kotlin.math.abs(battery), battery >= 0, deviceType, ip))
            }
        }
        NativeCore.lib.nrc_parse_heartbeat_tcp_with_cb(msg, cb, null)
        return result.firstOrNull()
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