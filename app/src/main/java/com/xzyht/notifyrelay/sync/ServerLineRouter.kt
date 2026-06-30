package com.xzyht.notifyrelay.sync

import android.content.Context
import android.widget.Toast
import com.xzyht.notifyrelay.feature.device.service.AuthInfo
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import kotlinx.coroutines.launch
import notifyrelay.base.util.Logger
import notifyrelay.core.util.BatteryUtils
import notifyrelay.core.util.EncryptionManager
import notifyrelay.core.util.EcdhKeyStore
import notifyrelay.core.util.PairingCodeManager
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.Socket

/**
 * 服务端首行协议路由器
 *
 * 设计目标：
 * - 让 `DeviceConnectionManager.startServer()` 只负责「接受连接 + 读首行」，
 *   把「这行是 HANDSHAKE / DATA / 其它」的判断与处理挪到这里，
 *   便于后续维护协议演进而不污染连接管理类。
 * - 不直接持有任何全局状态，只通过 `DeviceConnectionManager` 暴露的 internal 访问器
 *   读写设备缓存、认证表、心跳状态等。
 */
object ServerLineRouter {

    private const val TAG = "配对"

    /**
     * 分发首行协议到对应处理器
     *
     * @param line 首行协议文本
     * @param client 客户端 Socket
     * @param reader 读取流（用于后续数据读取，如需要）
     * @param deviceManager 设备管理器实例
     * @param context 上下文（用于获取系统服务等）
     */
    fun routeLine(
        line: String,
        client: Socket,
        reader: BufferedReader,
        deviceManager: DeviceConnectionManager,
        context: Context
    ) {
        // 按首行前缀分发：配对请求 / 已认证握手 / DATA 加密通道 / 其它
        when {
            line.startsWith("PAIRING_INIT:") -> handlePairingInit(line, client, reader, deviceManager)
            line.startsWith("PAIRING_RESP:") -> handlePairingResp(line, client, reader, deviceManager, context)
            line.startsWith("HANDSHAKE:") -> handleHandshake(line, client, reader, deviceManager)
            line.startsWith("DATA") -> handleData(line, client, reader, deviceManager, context)
            else -> handleOther(line, client, reader, deviceManager)
        }
    }

    /**
     * 处理 PAIRING_INIT：发起端通知接收端有配对请求，接收端弹输入框。
     * 格式：PAIRING_INIT:<uuid>:<pubKey>:<ip>:<battery>:<deviceType>
     */
    private fun handlePairingInit(
        line: String,
        client: Socket,
        reader: BufferedReader,
        deviceManager: DeviceConnectionManager
    ) {
        try {
            val parts = line.split(":")
            if (parts.size >= 6) {
                val remoteUuid = parts[1]
                val tmpPubKey = parts[2]  // 临时公钥
                val remoteIp: String = parts.getOrNull(3) ?: client.inetAddress.hostAddress.orEmpty().ifEmpty { "0.0.0.0" }
                val remoteDeviceType: String = parts.getOrNull(5) ?: "unknown"
                val ip: String = client.inetAddress.hostAddress.orEmpty().ifEmpty { "0.0.0.0" }

                // 检查是否已被拒绝
                if (deviceManager.rejectedDevicesInternal.contains(remoteUuid)) {
                    val writer = OutputStreamWriter(client.getOutputStream())
                    writer.write("REJECT:${deviceManager.uuid}\n")
                    writer.flush()
                    writer.close()
                    reader.close()
                    client.close()
                    return
                }

                // 缓存设备信息
                val displayName = deviceManager.deviceInfoCacheInternal[remoteUuid]?.displayName ?: "未知设备"
                deviceManager.deviceInfoCacheInternal[remoteUuid] = DeviceInfo(remoteUuid, displayName, ip, 23333)

                // 存储配对信息，包含临时公钥
                val pending = DeviceConnectionManager.PendingPairing(
                    remoteUuid = remoteUuid,
                    remotePubKey = tmpPubKey,
                    remoteIp = ip,
                    tmpPubKey = tmpPubKey
                )
                deviceManager.pendingPairing = pending

                // 触发 UI 显示配对码输入弹窗（使用新接口）
                val remoteDevice = DeviceInfo(remoteUuid, displayName, ip, 23333)
                deviceManager.handshakeRequestHandler?.onPairingInitRequest(remoteDevice, tmpPubKey)

                try { reader.close() } catch (_: Exception) {}
                try { client.close() } catch (_: Exception) {}
                Logger.d(TAG, "PAIRING_INIT 已处理: $remoteUuid")
            } else {
                try { reader.close() } catch (_: Exception) {}
                try { client.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Logger.e(TAG, "handlePairingInit error: ${e.message}")
            try { reader.close() } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
        }
    }

    /**
     * 处理 PAIRING_RESP：接收端用户输入了配对码，加密回传给发起端验证。
     * 发起端用临时私钥解密配对码，验证通过后执行 ECDH 密钥协商。
     * 格式：PAIRING_RESP:<uuid_R>:<tmpPub_R>:<ltPub_R>:<encryptedCode>:<ip>:<battery>:<deviceType>
     */
    private fun handlePairingResp(
        line: String,
        client: Socket,
        reader: BufferedReader,
        deviceManager: DeviceConnectionManager,
        context: android.content.Context
    ) {
        try {
            val parts = line.split(":")
            if (parts.size >= 8) {
                val remoteUuid = parts[1]
                val tmpPubKeyR = parts[2]       // 接收端的临时公钥
                val ltPubKeyR = parts[3]         // 接收端的长期 ECDH 公钥
                val encryptedCode = parts[4]     // 加密后的配对码
                val ip: String = client.inetAddress.hostAddress.orEmpty().ifEmpty { "0.0.0.0" }

                Logger.d(TAG, "收到 PAIRING_RESP: $remoteUuid")

                // 获取发起端存储的临时私钥
                val tmpPrivKeyB64 = deviceManager.pendingTempPrivKeyB64
                if (tmpPrivKeyB64 == null) {
                    Logger.w(TAG, "临时私钥不存在，配对已过期: $remoteUuid")
                    val writer = OutputStreamWriter(client.getOutputStream())
                    writer.write("REJECT:${deviceManager.uuid}\n")
                    writer.flush()
                    writer.close()
                    reader.close()
                    client.close()
                    return
                }

                try {
                    // 1. 用临时私钥解密配对码
                    val tmpPrivKey = EcdhKeyStore.decodePrivateKey(tmpPrivKeyB64)
                    val sharedSecret = EcdhKeyStore.deriveRawSharedSecret(tmpPrivKey, tmpPubKeyR)
                    val aesKey = EncryptionManager.hkdfDeriveKey(sharedSecret)
                    val code = EncryptionManager.decrypt(encryptedCode, aesKey)
                    
                    // 2. 验证配对码
                    if (!PairingCodeManager.verify(code)) {
                        Logger.w(TAG, "配对码验证失败: $remoteUuid")
                        val writer = OutputStreamWriter(client.getOutputStream())
                        writer.write("REJECT:${deviceManager.uuid}\n")
                        writer.flush()
                        writer.close()
                        reader.close()
                        client.close()
                        return
                    }
                    Logger.d(TAG, "配对码验证通过: $remoteUuid")

                    // 3. 使用接收端长期公钥完成标准密钥交换
                    val success = deviceManager.completePairingWithLongTermKeys(
                        remoteUuid, ltPubKeyR,
                        displayName = "未知设备",
                        lastIp = ip
                    )
                    if (!success) {
                        Logger.w(TAG, "密钥交换失败: $remoteUuid")
                        val writer = OutputStreamWriter(client.getOutputStream())
                        writer.write("REJECT:${deviceManager.uuid}\n")
                        writer.flush()
                        writer.close()
                        reader.close()
                        client.close()
                        return
                    }

                    // 4. ACCEPT 回传：包含发起端长期公钥
                    val writer = OutputStreamWriter(client.getOutputStream())
                    val localBattery = getLocalBatteryInfo(deviceManager)
                    val localDeviceType = "android"
                    val localIp = getLocalIpAddress(deviceManager)
                    writer.write("ACCEPT:$code:${deviceManager.uuid}:${deviceManager.localPublicKey}:$localIp:$localBattery:$localDeviceType\n")
                    writer.flush()
                    writer.close()
                    reader.close()
                    client.close()

                    // 5. 清理临时私钥
                    deviceManager.pendingTempPrivKeyB64 = null
                    Logger.d(TAG, "PAIRING_RESP 配对成功: $remoteUuid")
                } catch (e: Exception) {
                    Logger.e(TAG, "PAIRING_RESP 解密/验证失败: $remoteUuid", e)
                    try {
                        val writer = OutputStreamWriter(client.getOutputStream())
                        writer.write("REJECT:${deviceManager.uuid}\n")
                        writer.flush()
                        writer.close()
                    } catch (_: Exception) {}
                    try { reader.close() } catch (_: Exception) {}
                    try { client.close() } catch (_: Exception) {}
                }
            } else {
                try { reader.close() } catch (_: Exception) {}
                try { client.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Logger.e(TAG, "handlePairingResp error: ${e.message}")
            try { reader.close() } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
        }
    }

    /**
     * 处理握手请求：
     * - 更新/填充远端设备的 IP 信息到 `deviceInfoCache`
     * - 若已认证则直接回复 ACCEPT
     * - 否则触发 `onHandshakeRequest` 回调给 UI，由用户选择接受/拒绝
     * - 根据结果更新 `authenticatedDevices` / `rejectedDevices`
     * 握手格式：HANDSHAKE:<uuid>:<publicKey>:<ipAddress>:<batteryLevel>:<deviceType>
     */
    private fun handleHandshake(
        line: String,
        client: Socket,
        reader: BufferedReader,
        deviceManager: DeviceConnectionManager
    ) {
        try {
            val parts = line.split(":")
            if (parts.size >= 6) {
                val remoteUuid = parts[1]
                val remotePubKey = parts[2]
                val remoteIp: String = parts.getOrNull(3) ?: client.inetAddress.hostAddress.orEmpty().ifEmpty { "0.0.0.0" }
                val remoteBattery: String = parts.getOrNull(4) ?: ""
                val remoteDeviceType: String = parts.getOrNull(5) ?: "unknown"

                val ip: String = client.inetAddress.hostAddress.orEmpty().ifEmpty { "0.0.0.0" }

                // 1. 同步更新设备 IP 缓存，端口保持原有或默认
                synchronized(deviceManager.deviceInfoCacheInternal) {
                    val old = deviceManager.deviceInfoCacheInternal[remoteUuid]
                    val displayName = old?.displayName ?: "未知设备"
                    deviceManager.deviceInfoCacheInternal[remoteUuid] = DeviceInfo(
                        remoteUuid,
                        displayName,
                        ip,
                        old?.port ?: 23333
                    )
                }

                // 2. 若认证表中已有该设备，只更新 lastIp（不改端口）
                synchronized(deviceManager.authenticatedDevices) {
                    val auth = deviceManager.authenticatedDevices[remoteUuid]
                    if (auth != null) {
                        deviceManager.authenticatedDevices[remoteUuid] = auth.copy(lastIp = ip)
                        deviceManager.saveAuthedDevicesInternal()
                    }
                }

                // 3. 基于缓存构造远端设备信息，用于 UI 显示
                val remoteDevice = deviceManager.deviceInfoCacheInternal[remoteUuid]!!
                val alreadyAuthed = synchronized(deviceManager.authenticatedDevices) {
                    deviceManager.authenticatedDevices[remoteUuid]?.isAccepted == true
                }

                // 4. 已认证设备：检查公钥轮换
                if (alreadyAuthed) {
                    // 检查公钥是否发生变化（密钥轮换检测）
                    synchronized(deviceManager.authenticatedDevices) {
                        val existingAuth = deviceManager.authenticatedDevices[remoteUuid]
                        if (existingAuth != null && existingAuth.publicKey != remotePubKey) {
                            Logger.w(TAG, "设备 ${remoteDevice.displayName} 公钥已变更，重新派生密钥")
                            try {
                                val newSecret = EncryptionManager.generateSharedSecret(
                                    deviceManager.contextInternal,
                                    deviceManager.localPublicKey,
                                    remotePubKey
                                )
                                EncryptionManager.importAesKeyToKeystore(deviceManager.contextInternal, remoteUuid, newSecret)
                                deviceManager.authenticatedDevices[remoteUuid] = existingAuth.copy(
                                    publicKey = remotePubKey,
                                    sharedSecret = newSecret
                                )
                                deviceManager.saveAuthedDevicesInternal()
                                // 保存后清空内存中的明文，后续解密走 Keystore
                                deviceManager.authenticatedDevices[remoteUuid] = existingAuth.copy(
                                    publicKey = remotePubKey,
                                    sharedSecret = ""
                                )
                            } catch (e: Exception) {
                                Logger.e(TAG, "公钥变更后重新派生密钥失败，要求重新配对", e)
                                synchronized(deviceManager.incompatibleDevicesInternal) {
                                    deviceManager.incompatibleDevicesInternal.add(remoteUuid)
                                }
                                val writer = OutputStreamWriter(client.getOutputStream())
                                writer.write("REJECT:${deviceManager.uuid}\n")
                                writer.flush()
                                writer.close()
                                reader.close()
                                client.close()
                                return
                            }
                        }
                    }
                    // 从黑名单移除（如果有）
                    synchronized(deviceManager.incompatibleDevicesInternal) {
                        deviceManager.incompatibleDevicesInternal.remove(remoteUuid)
                    }
                    val writer = OutputStreamWriter(client.getOutputStream())
                    val localBattery = getLocalBatteryInfo(deviceManager)
                    val localDeviceType = "android"
                    val localIp = getLocalIpAddress(deviceManager)
                    writer.write("ACCEPT:${deviceManager.uuid}:${deviceManager.localPublicKey}:$localIp:$localBattery:$localDeviceType\n")
                    writer.flush()
                    writer.close()
                    reader.close()
                    client.close()
                    
                    // 更新已认证设备的 deviceType 和 lastIp
                    synchronized(deviceManager.authenticatedDevices) {
                        val auth = deviceManager.authenticatedDevices[remoteUuid]
                        if (auth != null) {
                            deviceManager.authenticatedDevices[remoteUuid] = auth.copy(
                                deviceType = remoteDeviceType,
                                lastIp = ip
                            )
                            deviceManager.saveAuthedDevicesInternal()
                            Logger.d(TAG, "已更新已认证设备的 deviceType: $remoteDeviceType, lastIp: $ip")
                        }
                    }
                } else {
                    // 未认证设备：不再支持通过 HANDSHAKE 配对，请使用配对码流程
                    val writer = OutputStreamWriter(client.getOutputStream())
                    writer.write("REJECT:${deviceManager.uuid}\n")
                    writer.flush()
                    writer.close()
                    reader.close()
                    client.close()
                }
            } else {
                val writer = OutputStreamWriter(client.getOutputStream())
                writer.write("REJECT:${deviceManager.uuid}\n")
                writer.flush()
                writer.close()
                reader.close()
                client.close()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "handleHandshake error: ${e.message}")
            try { reader.close() } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
        }
    }

    /**
     * 处理 DATA* 加密通道：
     * - 这里不做具体业务解析，只负责把首行和 client IP 交给 ProtocolRouter
     * - ProtocolRouter 再统一完成解密和按 DATA_* 头进行的业务路由
     */
    private fun handleData(
        line: String,
        client: Socket,
        reader: BufferedReader,
        deviceManager: DeviceConnectionManager,
        context: Context
    ) {
        try {
            val clientIp = client.inetAddress?.hostAddress ?: "0.0.0.0"
            // DATA* 加密通道统一交给 ProtocolRouter 处理（解密+业务路由）
            ProtocolRouter.handleEncryptedDataLine(line, clientIp, deviceManager, context)
        } catch (_: Exception) {
        } finally {
            try { reader.close() } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
        }
    }

    /**
     * 处理其它首行协议：
     * - 当前 only 用于处理手动发现 NOTIFYRELAY_DISCOVER_MANUAL（加密文本）
     * - 尝试用每个已认证设备的 sharedSecret 解密首行
     * - 匹配成功后更新 deviceInfoCache / authenticatedDevices 的 IP / 端口等信息
     * - HEARTBEAT_TCP：处理TCP心跳包，更新设备在线状态
     */
    private fun handleOther(
        line: String,
        client: Socket,
        reader: BufferedReader,
        deviceManager: DeviceConnectionManager
    ) {
        try {
            if (line.startsWith("NOTIFYRELAY_DISCOVER_MANUAL:")) {
                val encryptedPart = line.substringAfter("NOTIFYRELAY_DISCOVER_MANUAL:")
                val clientIp = client.inetAddress.hostAddress.orEmpty()

                // 尝试用每个已认证设备的 sharedSecret 解密
                synchronized(deviceManager.authenticatedDevices) {
                    for ((uuid, auth) in deviceManager.authenticatedDevices) {
                        try {
                            val decrypted = deviceManager.decryptDataInternal(encryptedPart, uuid)
                            if (decrypted.startsWith("NOTIFYRELAY_DISCOVER:")) {
                                // 解密成功：更新设备缓存
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
                                        //Logger.d(TAG, "收到手动发现UDP: $decrypted, ip=$ip, uuid=$remoteUuid")
                                    }
                                }
                                break
                            }
                        } catch (_: Exception) {
                            // 解密失败，继续尝试下一个密钥
                        }
                    }
                }
            } else if (line.startsWith("HEARTBEAT_TCP:")) {
                val payload = line.substringAfter("HEARTBEAT_TCP:")
                val clientIp = client.inetAddress.hostAddress.orEmpty()
                val heartbeatInfo = HeartbeatProcessor.parseHeartbeatPayload(payload, clientIp, deviceManager.listenPort)
                if (heartbeatInfo != null && heartbeatInfo.uuid != deviceManager.uuid) {
                    HeartbeatProcessor.processHeartbeat(heartbeatInfo, deviceManager)
                }
            }
        } catch (_: Exception) {
        } finally {
            try { reader.close() } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun getLocalBatteryInfo(deviceManager: DeviceConnectionManager): String {
        return try {
            val batteryLevel = BatteryUtils.getBatteryLevel(deviceManager.contextInternal)
            val isCharging = BatteryUtils.isCharging(deviceManager.contextInternal)
            if (isCharging) "$batteryLevel+" else "$batteryLevel"
        } catch (_: Exception) {
            ""
        }
    }

    private fun getLocalIpAddress(deviceManager: DeviceConnectionManager): String {
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
