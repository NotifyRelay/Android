package com.xzyht.notifyrelay.sync

import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.nativecore.NativeCore
import notifyrelay.core.util.BatteryUtils
import java.net.Inet4Address
import java.net.NetworkInterface

internal fun getLocalIpAddress(): String {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback || !networkInterface.isUp) continue

            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    return address.hostAddress ?: "0.0.0.0"
                }
            }
        }
        "0.0.0.0"
    } catch (e: Exception) {
        "0.0.0.0"
    }
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
