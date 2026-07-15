package com.xzyht.notifyrelay.sync

import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import com.xzyht.notifyrelay.nativecore.NativeCore
import notifyrelay.base.util.Logger
import notifyrelay.core.util.BatteryUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * 与协议相关的底层传输封装：配对 / 握手 / 心跳 / 发现广播。
 *
 * 这里仅负责文本报文的拼装与发送，不做业务状态变更，
 * 由上层 `DeviceConnectionManager` 根据返回结果更新认证、心跳等状态。
 */

/** 统一握手发送器 */
object HandshakeSender {

    private const val TAG = "配对"

    /**
     * 发起端发送 PAIRING_INIT（不含配对码），通知接收端有配对请求。
     * 格式：PAIRING_INIT:<uuid>:<tmpPublicKey>:<ipAddress>:<batteryLevel>:<deviceType>
     * @param tmpPublicKey 发起端的临时 ECDH 公钥 Base64，用于接收端加密回传配对码
     */
    fun sendPairingInit(
        manager: DeviceConnectionManager,
        target: DeviceInfo,
        tmpPublicKey: String,
        connectTimeoutMs: Int = 3000
    ): String? {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(target.ip, target.port), connectTimeoutMs)
                socket.soTimeout = connectTimeoutMs
                val writer = OutputStreamWriter(socket.getOutputStream())
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                val batteryLevel = BatteryUtils.getBatteryLevel(manager.contextInternal)
                val isCharging = BatteryUtils.isCharging(manager.contextInternal)
                val battery = if (isCharging) batteryLevel else -batteryLevel
                val localIp = getLocalIpAddress()

                val req = NativeCore.formatPairingInit(manager.uuid, tmpPublicKey, localIp, battery, "android")
                    ?: return null
                writer.write(req + "\n")
                writer.flush()
                reader.readLine()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "sendPairingInit 失败: ${target.uuid}@${target.ip}:${target.port}", e)
            null
        }
    }

    /**
     * 接收端收到 PAIRING_INIT 后，用户输入配对码，通过 PAIRING_RESP 回传给发起端。
     * 格式：PAIRING_RESP:<uuid>:<tmpPublicKey>:<ltPublicKey>:<encryptedCode>:<ipAddress>:<batteryLevel>:<deviceType>
     * @param tmpPublicKey 接收端的临时 ECDH 公钥（用于发起端解密配对码）
     * @param encryptedCode 已加密的配对码
     */
    fun sendPairingResp(
        manager: DeviceConnectionManager,
        target: DeviceInfo,
        tmpPublicKey: String,
        encryptedCode: String,
        connectTimeoutMs: Int = 3000
    ): String? {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(target.ip, target.port), connectTimeoutMs)
                socket.soTimeout = connectTimeoutMs
                val writer = OutputStreamWriter(socket.getOutputStream())
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                val batteryLevel = BatteryUtils.getBatteryLevel(manager.contextInternal)
                val isCharging = BatteryUtils.isCharging(manager.contextInternal)
                val battery = if (isCharging) batteryLevel else -batteryLevel
                val localIp = getLocalIpAddress()

                val req = NativeCore.formatPairingResp(
                    manager.uuid, tmpPublicKey, manager.localPublicKey,
                    encryptedCode, localIp, battery, "android"
                ) ?: return null
                writer.write(req + "\n")
                writer.flush()
                reader.readLine()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "sendPairingResp 失败: ${target.uuid}@${target.ip}:${target.port}", e)
            null
        }
    }

    /**
     * 已配对设备重连时发起握手，复用已有密钥。
     * 格式：HANDSHAKE:<uuid>:<publicKey>:<ipAddress>:<batteryLevel>:<deviceType>
     */
    fun sendHandshake(
        manager: DeviceConnectionManager,
        target: DeviceInfo,
        connectTimeoutMs: Int = 3000
    ): String? {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(target.ip, target.port), connectTimeoutMs)
                socket.soTimeout = connectTimeoutMs
                val writer = OutputStreamWriter(socket.getOutputStream())
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                val batteryLevel = BatteryUtils.getBatteryLevel(manager.contextInternal)
                val isCharging = BatteryUtils.isCharging(manager.contextInternal)
                val battery = if (isCharging) batteryLevel else -batteryLevel
                val localIp = getLocalIpAddress()

                val handshake = NativeCore.formatHandshake(
                    manager.uuid, manager.localPublicKey, localIp, battery, "android"
                ) ?: return null
                writer.write(handshake + "\n")
                writer.flush()
                reader.readLine()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "sendHandshake 失败: ${target.uuid}@${target.ip}:${target.port}", e)
            null
        }
    }

    private fun getLocalIpAddress(): String {
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

    /**
     * 通过TCP发送心跳（用于锁屏时UDP广播被限制的情况）
     * 格式：HEARTBEAT_TCP:<uuid>:<displayName>:<port>:<+/-><batteryLevel>:<deviceType>
     */
    fun sendTcpHeartbeat(
        manager: DeviceConnectionManager,
        target: DeviceInfo,
        timeoutMs: Int = 3000
    ): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(target.ip, target.port), timeoutMs)
            val writer = OutputStreamWriter(socket.getOutputStream())

            val batteryLevel = BatteryUtils.getBatteryLevel(manager.contextInternal)
            val isCharging = BatteryUtils.isCharging(manager.contextInternal)
            val battery = if (isCharging) batteryLevel else -batteryLevel
            val displayName = manager.localDisplayNameInternal()
            val port = manager.listenPort
            val payload = NativeCore.formatTcpHeartbeat(
                manager.uuid, displayName, port.toShort(), battery, "android"
            ) ?: return false

            writer.write(payload + "\n")
            writer.flush()
            try { writer.close() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
            true
        } catch (e: Exception) {
            false
        }
    }
}

/** 统一广播发送器（用于发现和心跳广播） */
object DiscoveryBroadcaster {

    private const val TAG = "DiscoveryBroadcaster"
    private const val BROADCAST_PORT = 23334 // 广播端口

    fun sendBroadcast(manager: DeviceConnectionManager): Boolean {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.broadcast = true
            val batteryLevel = BatteryUtils.getBatteryLevel(manager.contextInternal)
            val isCharging = BatteryUtils.isCharging(manager.contextInternal)
            val battery = if (isCharging) batteryLevel else -batteryLevel
            val displayName = manager.localDisplayNameInternal()
            val port = manager.listenPort
            val payload = NativeCore.formatDiscovery(
                manager.uuid, displayName, port.toShort(), battery, "android"
            ) ?: return false
            val buf = payload.toByteArray()
            val address = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(buf, buf.size, address, BROADCAST_PORT)
            socket.send(packet)
            true
        } catch (e: Exception) {
            false
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
