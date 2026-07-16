package com.xzyht.notifyrelay.sync

import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.nativecore.NativeCore
import notifyrelay.core.util.BatteryUtils

internal fun getLocalIpAddress(): String {
    return NativeCore.getLocalIp() ?: "0.0.0.0"
}

object DiscoveryBroadcaster {

    fun sendBroadcast(manager: DeviceConnectionManager): Boolean {
        val ctx = manager.rustContextInternal ?: return false
        val batteryLevel = BatteryUtils.getBatteryLevel(manager.contextInternal)
        val isCharging = BatteryUtils.isCharging(manager.contextInternal)
        val battery = if (isCharging) batteryLevel else -batteryLevel
        val displayName = manager.localDisplayNameInternal()
        val port = manager.listenPort
        return try {
            NativeCore.sendDiscovery(ctx, manager.uuid, displayName, port.toShort(), battery, "android")
            true
        } catch (e: Exception) {
            false
        }
    }
}
