package com.xzyht.notifyrelay.sync

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.xzyht.notifyrelay.feature.device.service.AuthInfo
import notifyrelay.core.util.EncryptionManager
import notifyrelay.base.util.Logger
import notifyrelay.base.util.PermissionHelper
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerUtil
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.collections.iterator

/**
 * 连接保活与重连策略封装：
 *
 * - 对「已经成功握手并建立信任」的设备：
 *   - 启动心跳循环（定期发送 HEARTBEAT 报文），
 *   - 跟踪每个设备的心跳任务 Job 与心跳成功状态；
 *
 * - 当心跳连续失败达到阈值时：
 *   - 停止该设备的心跳任务、移除 heartbeated 标记，
 *   - 读取最近一次的 IP/端口信息，尝试多次 connectToDevice 重连，
 *   - 若仍然失败，通过 Toast 提示用户设备离线；
 *
 * - 在 WLAN 直连环境下：
 *   - 周期性扫描所有已认证但未心跳的设备，对其做保守的主动重连尝试。
 *
 * 同时，这里还承接「首次连接」时的握手重试逻辑：
 *   - performDeviceConnectionWithRetry 统一处理握手重试 + 认证成功后的状态更新 + 启动心跳等，
 *   - 让 DeviceConnectionManager.connectToDevice 变成一个较薄的入口。
 *
 * 注意：本类只通过 DeviceConnectionManager 暴露的 internal 视图读写状态，
 * 不直接操作其 private 字段，保持边界清晰。
 */
class ConnectionKeepAlive(
    private val deviceManager: DeviceConnectionManager,
    private val scope: CoroutineScope
) {
    private val heartbeatJobs get() = deviceManager.heartbeatJobsInternal
    private val heartbeatedDevices get() = deviceManager.heartbeatedDevicesInternal
    private val authenticatedDevices get() = deviceManager.authenticatedDevices
    private var lastUsedTcpHeartbeat = false

    /**
     * 启动某个设备的心跳任务。
     * - 每 4 秒发送一次心跳
     * - 连续失败 5 次后触发 handleHeartbeatFailure
     * - 锁屏时或WLAN直连模式下使用TCP心跳，否则使用UDP广播
     */
    fun startHeartbeatToDevice(uuid: String, initialIp: String, initialPort: Int, sharedSecret: String) {
        heartbeatJobs[uuid]?.cancel()
        heartbeatedDevices.add(uuid)
        //Logger.d("死神-NotifyRelay", "[KeepAlive] startHeartbeatToDevice: uuid=$uuid, ip=$initialIp, port=$initialPort")

        val job = scope.launch {
            var failCount = 0
            val maxFail = 5
            while (true) {
                var success = false
                try {
                    val info = deviceManager.getDeviceInfoInternal(uuid)
                    val auth = synchronized(deviceManager.authenticatedDevices) { deviceManager.authenticatedDevices[uuid] }
                    val targetIp = info?.ip ?: auth?.lastIp ?: initialIp
                    val targetPort = info?.port ?: auth?.lastPort ?: initialPort

                    // 判断是否需要使用TCP心跳：锁屏时或WLAN直连模式下使用TCP
                    val useTcpHeartbeat = shouldUseTcpHeartbeat()

                    if (useTcpHeartbeat && targetIp.isNotEmpty() && targetIp != "0.0.0.0") {
                        // TCP心跳：向对方主动建立TCP连接发送心跳
                        success = HeartbeatSender.sendTcpHeartbeat(
                            deviceManager,
                            DeviceInfo(uuid, info?.displayName ?: auth?.displayName ?: "已认证设备", targetIp, targetPort)
                        )
                    } else {
                        // UDP广播心跳
                        success = try {
                            DiscoveryBroadcaster.sendBroadcast(deviceManager)
                        } catch (e: Exception) {
                            false
                        }
                    }
                } catch (e: Exception) {
                    //Logger.d("死神-NotifyRelay", "[KeepAlive] 心跳发送失败: $uuid, ${e.message}")
                }

                if (success) {
                    failCount = 0
                } else {
                    failCount++
                    if (failCount >= maxFail) {
                        handleHeartbeatFailure(uuid)
                        break
                    }
                }
                delay(4000)
            }
        }
        heartbeatJobs[uuid] = job
    }

    /**
     * 判断是否应该使用TCP心跳而非UDP广播
     * 条件：本机锁屏时 或 WLAN直连模式
     */
    private fun shouldUseTcpHeartbeat(): Boolean {
        val useTcp = deviceManager.isWifiDirectNetworkInternal() ||
                     PermissionHelper.isDeviceLocked(deviceManager.contextInternal)

        if (useTcp != lastUsedTcpHeartbeat) {
            lastUsedTcpHeartbeat = useTcp
            if (useTcp) {
                Logger.d("KeepAlive", "切换到TCP心跳")
            } else {
                Logger.d("KeepAlive", "切换到UDP广播心跳")
            }
        }

        return useTcp
    }

    /**
     * 心跳连续失败后的处理逻辑：
     * - 取消心跳任务，移除已心跳标记
     * - 尝试最多 3 次重连
     * - 失败后通过 Toast 提示用户
     */
    fun handleHeartbeatFailure(uuid: String) {
        Logger.w("死神-NotifyRelay", "[KeepAlive] 心跳连续失败5次，自动停止心跳并尝试重连: $uuid")
        heartbeatJobs[uuid]?.cancel()
        heartbeatJobs.remove(uuid)
        heartbeatedDevices.remove(uuid)

        scope.launch {
            val info = deviceManager.getDeviceInfoInternal(uuid)
            val auth = synchronized(deviceManager.authenticatedDevices) { deviceManager.authenticatedDevices[uuid] }
            val ip = info?.ip ?: auth?.lastIp
            val port = info?.port ?: auth?.lastPort ?: deviceManager.listenPort
            val displayName = info?.displayName ?: auth?.displayName ?: "已认证设备"
            if (!ip.isNullOrEmpty() && ip != "0.0.0.0") {
                for (attempt in 1..3) {
                    //Logger.d("死神-NotifyRelay", "[KeepAlive] 心跳失败后重连尝试 $attempt/3: $uuid, $ip:$port")
                    deviceManager.connectToDevice(DeviceInfo(uuid, displayName, ip, port))
                    delay(2000)
                    if (heartbeatedDevices.contains(uuid)) {
                        //Logger.d("死神-NotifyRelay", "[KeepAlive] 心跳失败后重连成功: $uuid")
                        return@launch
                    }
                }
                Logger.w("死神-NotifyRelay", "[KeepAlive] 心跳失败后重连失败，设备离线: $uuid")
            }
        }

        val msg = "设备[${DeviceConnectionManagerUtil.getDisplayNameByUuid(uuid)}]离线，已尝试重连，请检查网络或重新发现设备"
        val ctx = deviceManager.contextInternal
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * WLAN 直连模式下的定期重连检查：
     * - 每 30 秒检查一次
     * - 对于未在 heartbeatedDevices 中的认证设备，尝试重连
     */
    fun startWifiDirectReconnectionChecker() {
        scope.launch {
            while (true) {
                delay(30_000)
                if (deviceManager.isWifiDirectNetworkInternal()) {
                    val authed = synchronized(deviceManager.authenticatedDevices) { deviceManager.authenticatedDevices.toMap() }
                    //Logger.d("死神-NotifyRelay", "[KeepAlive] WLAN直连定期检查：${authed.size}个认证设备")

                    for ((deviceUuid, auth) in authed) {
                        if (deviceUuid == deviceManager.uuid) continue
                        val isOnline = heartbeatedDevices.contains(deviceUuid)
                        if (!isOnline) {
                            val info = deviceManager.getDeviceInfoInternal(deviceUuid)
                            val ip = info?.ip ?: auth.lastIp
                            val port = info?.port ?: auth.lastPort ?: deviceManager.listenPort
                            if (!ip.isNullOrEmpty() && ip != "0.0.0.0") {
                                //Logger.d("死神-NotifyRelay", "[KeepAlive] WLAN直连定期重连离线设备: $deviceUuid, $ip:$port")
                                deviceManager.connectToDevice(DeviceInfo(deviceUuid, auth.displayName ?: "WLAN直连设备", ip, port))
                                delay(2000)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 封装设备连接握手与重试逻辑：
     * - 最多重试 maxRetries 次
     * - 认证成功后更新 AuthInfo/deviceInfoCache 并启动心跳
     * - 失败原因通过 (Boolean, String?) 形式返回
     */
    suspend fun performDeviceConnectionWithRetry(device: DeviceInfo, maxRetries: Int): Pair<Boolean, String?> {
        var lastException: Exception? = null

        for (retry in 0 until maxRetries) {
            try {
                val resp = HandshakeSender.sendHandshake(deviceManager, device, 3000)
                //Logger.d("死神-NotifyRelay", "connectToDevice: handshake resp=$resp")

                if (resp != null && resp.startsWith("ACCEPT:")) {
                    val parts = resp.split(":")
                    if (parts.size >= 3) {
                        val remotePubKey = parts[2]
                        val sharedSecret = EncryptionManager.generateSharedSecret(
                            deviceManager.contextInternal,
                            deviceManager.localPublicKey,
                            remotePubKey
                        )
                        EncryptionManager.importAesKeyToKeystore(
                            deviceManager.contextInternal,
                            device.uuid,
                            sharedSecret
                        )
                        deviceManager.migrateKeyToRust(device.uuid, sharedSecret)
                        synchronized(authenticatedDevices) {
                            authenticatedDevices.remove(device.uuid)
                            authenticatedDevices[device.uuid] = AuthInfo(
                                remotePubKey,
                                "",
                                true,
                                device.displayName,
                                device.ip,
                                device.port
                            )
                            deviceManager.saveAuthedDevicesInternal()
                        }
                        synchronized(deviceManager.deviceInfoCacheInternal) {
                            deviceManager.deviceInfoCacheInternal[device.uuid] = device
                        }
                        //Logger.d("死神-NotifyRelay", "认证成功，启动心跳: uuid=${device.uuid}, ip=${device.ip}, port=${device.port}")
                        startHeartbeatToDevice(device.uuid, device.ip, device.port, sharedSecret)
                        deviceManager.deviceLastSeenInternal[device.uuid] = System.currentTimeMillis()
                        try {
                            scope.launch {
                                deviceManager.updateDeviceListInternal()
                            }
                        } catch (_: Exception) {}

                        if (device.uuid != deviceManager.uuid) {
                            val myInfo = deviceManager.getDeviceInfoInternal(deviceManager.uuid)
                            if (myInfo != null) {
                                if (!heartbeatedDevices.contains(device.uuid)) {
                                    //Logger.d("死神-NotifyRelay", "认证成功后自动反向connectToDevice: myInfo=$myInfo, peer=${device.uuid}")
                                    deviceManager.connectToDevice(myInfo)
                                } else {
                                    //Logger.d("死神-NotifyRelay", "对方已建立心跳，不再反向connectToDevice: peer=${device.uuid}")
                                }
                            } else {
                                //Logger.d("死神-NotifyRelay", "本机getDeviceInfo返回null，无法反向connectToDevice")
                            }
                        }
                        return Pair(true, null)
                    } else {
                        //Logger.d("死神-NotifyRelay", "认证响应格式错误: $resp")
                        return Pair(false, "认证响应格式错误")
                    }
                } else if (resp != null && resp.startsWith("REJECT:")) {
                    //Logger.d("死神-NotifyRelay", "对方拒绝连接: uuid=${device.uuid}")
                    return Pair(false, "对方拒绝连接")
                } else {
                    //Logger.d("死神-NotifyRelay", "认证失败: resp=$resp")
                    return Pair(false, "认证失败")
                }
            } catch (e: UnsupportedOperationException) {
                // 格式不匹配：无需重试，直接提示用户升级
                val ctx = deviceManager.contextInternal
                val displayName = device.displayName
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(ctx, "设备 $displayName 使用旧版加密协议，请在对方设备上升级 NotifyRelay", android.widget.Toast.LENGTH_LONG).show()
                }
                Logger.e("死神-NotifyRelay", "connectToDevice 格式不匹配: ${e.message}")
                return Pair(false, e.message)
            } catch (e: Exception) {
                lastException = e
                //Logger.d("死神-NotifyRelay", "connectToDevice重试 $retry 失败: ${e.message}")
                if (retry < maxRetries - 1) {
                    delay(1000)
                }
            }
        }

        Logger.e("死神-NotifyRelay", "connectToDevice所有重试失败: ${lastException?.message}")
        return Pair(false, lastException?.message ?: "连接失败")
    }
}
