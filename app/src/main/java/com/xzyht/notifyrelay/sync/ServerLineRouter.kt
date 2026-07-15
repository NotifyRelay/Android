package com.xzyht.notifyrelay.sync

import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import com.xzyht.notifyrelay.nativecore.NativeCore
import notifyrelay.base.util.Logger
import notifyrelay.core.util.BatteryUtils
import java.io.BufferedReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.Socket

/**
 * 服务端首行协议路由器
 *
 * 所有协议消息统一走 Rust [NativeCore.processLine] 分发：
 * - DATA 消息：Rust 解密后通过注册的 DATA 回调分发
 * - 非 DATA 消息：Rust 解码后通过注册的非 DATA 回调分发（携带结构化参数字段）
 *
 * 回调执行期间通过 [sessionLocal] 传递 TCP 会话上下文。
 */
object ServerLineRouter {

    private const val TAG = "配对"

    // ==================== 回调线程上下文传递 ====================

    /** 供 Rust 回调线程获取当前 TCP 会话上下文 */
    data class SessionContext(
        val client: Socket,
        val reader: BufferedReader,
        val deviceManager: DeviceConnectionManager
    )

    private val sessionLocal = object : ThreadLocal<SessionContext>() {}

    /** Rust 回调从中获取当前会话上下文 */
    fun getSessionContext(): SessionContext? = sessionLocal.get()

    // ==================== 统一路由入口 ====================

    /**
     * 统一路由：所有消息类型均走 Rust [NativeCore.processLine]。
     *
     * 返回值语义：
     * - `0` — DATA 消息，处理完毕可断开连接
     * - `1` — 非 DATA 消息，回调已处理连接生命周期
     * - `-1` — 处理失败，回落 [handleOther]（用于 DISCOVER_MANUAL 等特殊格式）
     */
    fun routeLine(
        line: String,
        client: Socket,
        reader: BufferedReader,
        deviceManager: DeviceConnectionManager
    ) {
        val ctx = deviceManager.rustContextInternal
        if (ctx == null) {
            handleOther(line, client, reader, deviceManager)
            return
        }

        sessionLocal.set(SessionContext(client, reader, deviceManager))
        try {
            val result = NativeCore.processLine(ctx, line)
            if (result == -1) {
                if (line.startsWith("NOTIFYRELAY_DISCOVER_MANUAL:")) {
                    handleOther(line, client, reader, deviceManager)
                } else {
                    Logger.w(TAG, "processLine 处理失败: $line")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "processLine 异常", e)
        } finally {
            sessionLocal.remove()
            try { reader.close() } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
        }
    }

    /**
     * 回落处理：processLine 无法识别的消息（当前用于 DISCOVER_MANUAL 手动发现）
     */
    internal fun handleOther(
        line: String,
        client: Socket,
        reader: BufferedReader,
        deviceManager: DeviceConnectionManager
    ) {
        try {
            if (line.startsWith("NOTIFYRELAY_DISCOVER_MANUAL:")) {
                val encryptedPart = line.substringAfter("NOTIFYRELAY_DISCOVER_MANUAL:")
                val clientIp = client.inetAddress.hostAddress.orEmpty()

                synchronized(deviceManager.authenticatedDevices) {
                    for ((uuid, auth) in deviceManager.authenticatedDevices) {
                        try {
                            val decrypted = deviceManager.decryptDataInternal(encryptedPart, uuid)
                            if (decrypted.startsWith("NOTIFYRELAY_DISCOVER:")) {
                                val parts = decrypted.split(":")
                                if (parts.size >= 4) {
                                    val remoteUuid = parts[1]
                                    val rawDisplay = parts[2]
                                    val displayName = try {
                                        deviceManager.decodeDisplayNameFromTransportInternal(rawDisplay)
                                    } catch (_: Exception) {
                                        rawDisplay
                                    }
                                    val port = parts[3].toIntOrNull() ?: deviceManager.listenPort
                                    if (remoteUuid == uuid && !clientIp.isNullOrEmpty() && uuid != deviceManager.uuid) {
                                        val device = DeviceInfo(uuid, displayName, clientIp, port)
                                        synchronized(deviceManager.deviceInfoCacheInternal) {
                                            deviceManager.deviceInfoCacheInternal[uuid] = device
                                        }
                                    }
                                }
                                break
                            }
                        } catch (_: Exception) { }
                    }
                }
            } else if (line.startsWith("HEARTBEAT_TCP:")) {
                val clientIp = client.inetAddress.hostAddress.orEmpty()
                val heartbeatInfo = HeartbeatProcessor.parseHeartbeatTcpPayload(line, clientIp, deviceManager.listenPort)
                if (heartbeatInfo != null && heartbeatInfo.uuid != deviceManager.uuid) {
                    HeartbeatProcessor.processHeartbeat(heartbeatInfo, deviceManager)
                }
            }
        } catch (_: Exception) { }
    }

    internal fun getLocalBatteryInfo(deviceManager: DeviceConnectionManager): String {
        return try {
            val batteryLevel = BatteryUtils.getBatteryLevel(deviceManager.contextInternal)
            val isCharging = BatteryUtils.isCharging(deviceManager.contextInternal)
            if (isCharging) "$batteryLevel+" else "$batteryLevel"
        } catch (_: Exception) {
            ""
        }
    }

    internal fun getLocalIpAddress(deviceManager: DeviceConnectionManager): String {
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
        } catch (_: Exception) {
            "0.0.0.0"
        }
    }
}
