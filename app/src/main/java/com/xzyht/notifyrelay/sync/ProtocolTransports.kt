package com.xzyht.notifyrelay.sync

import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import com.xzyht.notifyrelay.nativecore.NativeCore
import notifyrelay.base.util.Logger
import notifyrelay.core.util.BatteryUtils
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 与协议相关的底层传输封装：配对 / 握手 / 心跳 / 发现广播。
 *
 * 这里仅负责文本报文的拼装与发送，不做业务状态变更，
 * 由上层 `DeviceConnectionManager` 根据返回结果更新认证、心跳等状态。
 */

/** 统一握手发送器 */
object HandshakeSender {

    private const val TAG = "配对"

    fun sendPairingInit(
        manager: DeviceConnectionManager,
        target: DeviceInfo,
        tmpPublicKey: String,
        connectTimeoutMs: Int = 3000
    ): String? {
        val batteryLevel = BatteryUtils.getBatteryLevel(manager.contextInternal)
        val isCharging = BatteryUtils.isCharging(manager.contextInternal)
        val battery = if (isCharging) batteryLevel else -batteryLevel
        val localIp = getLocalIpAddress()
        Logger.d(TAG, "sendPairingInit: target=${target.uuid}@${target.ip}:${target.port}, localIp=$localIp")
        val req = NativeCore.formatPairingInit(manager.uuid, tmpPublicKey, localIp, battery, "android")
            ?: return null
        return try {
            OneShotTcpClient.sendAndReceive(target.ip, target.port, req, connectTimeoutMs)
        } catch (e: Exception) {
            Logger.e(TAG, "sendPairingInit 失败: ${target.uuid}@${target.ip}:${target.port}", e)
            null
        }
    }

    fun sendPairingResp(
        manager: DeviceConnectionManager,
        target: DeviceInfo,
        tmpPublicKey: String,
        encryptedCode: String,
        connectTimeoutMs: Int = 3000
    ): String? {
        val batteryLevel = BatteryUtils.getBatteryLevel(manager.contextInternal)
        val isCharging = BatteryUtils.isCharging(manager.contextInternal)
        val battery = if (isCharging) batteryLevel else -batteryLevel
        val localIp = getLocalIpAddress()
        val req = NativeCore.formatPairingResp(
            manager.uuid, tmpPublicKey, manager.localPublicKey,
            encryptedCode, localIp, battery, "android"
        ) ?: return null
        return try {
            OneShotTcpClient.sendAndReceive(target.ip, target.port, req, connectTimeoutMs)
        } catch (e: Exception) {
            Logger.e(TAG, "sendPairingResp 失败: ${target.uuid}@${target.ip}:${target.port}", e)
            null
        }
    }

    fun sendHandshake(
        manager: DeviceConnectionManager,
        target: DeviceInfo,
        connectTimeoutMs: Int = 3000
    ): String? {
        val batteryLevel = BatteryUtils.getBatteryLevel(manager.contextInternal)
        val isCharging = BatteryUtils.isCharging(manager.contextInternal)
        val battery = if (isCharging) batteryLevel else -batteryLevel
        val localIp = getLocalIpAddress()
        val handshake = NativeCore.formatHandshake(
            manager.uuid, manager.localPublicKey, localIp, battery, "android"
        ) ?: return null
        return try {
            OneShotTcpClient.sendAndReceive(target.ip, target.port, handshake, connectTimeoutMs)
        } catch (e: Exception) {
            Logger.e(TAG, "sendHandshake 失败: ${target.uuid}@${target.ip}:${target.port}", e)
            null
        }
    }

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
}

/** 统一心跳发送器 */
object HeartbeatSender {

    private const val TAG = "HeartbeatSender"

    fun sendTcpHeartbeat(
        manager: DeviceConnectionManager,
        target: DeviceInfo,
        timeoutMs: Int = 3000
    ): Boolean {
        val batteryLevel = BatteryUtils.getBatteryLevel(manager.contextInternal)
        val isCharging = BatteryUtils.isCharging(manager.contextInternal)
        val battery = if (isCharging) batteryLevel else -batteryLevel
        val displayName = manager.localDisplayNameInternal()
        val port = manager.listenPort
        val payload = NativeCore.formatTcpHeartbeat(
            manager.uuid, displayName, port.toShort(), battery, "android"
        ) ?: return false
        return OneShotTcpClient.sendOnly(target.ip, target.port, payload, timeoutMs)
    }
}

/** 统一广播发送器（用于发现和心跳广播） */
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
