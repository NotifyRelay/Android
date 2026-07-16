package com.xzyht.notifyrelay.sync

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.xzyht.notifyrelay.feature.device.service.AuthInfo
import notifyrelay.base.util.Logger
import notifyrelay.base.util.PermissionHelper
import notifyrelay.core.util.BatteryUtils
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerUtil
import com.xzyht.notifyrelay.nativecore.NativeCore
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
     * 使用 Rust heartbeat sender 发送心跳，支持动态切换 TCP/UDP 模式。
     */
    fun startHeartbeatToDevice(uuid: String, initialIp: String, initialPort: Int, sharedSecret: String) {
        stopHeartbeatToDevice(uuid)
        heartbeatedDevices.add(uuid)

        val ctx = deviceManager.rustContextInternal ?: return
        val batteryLevel = BatteryUtils.getBatteryLevel(deviceManager.contextInternal)
        val isCharging = BatteryUtils.isCharging(deviceManager.contextInternal)
        val battery = if (isCharging) batteryLevel else -batteryLevel
        val displayName = deviceManager.localDisplayNameInternal()
        val mode = if (shouldUseTcpHeartbeat()) 1L else 0L

        val handle = NativeCore.startHeartbeatSender(ctx, uuid, displayName, battery, "android", 4000L, mode.toInt())
        heartbeatJobs[uuid] = handle
    }

    private fun stopHeartbeatToDevice(uuid: String) {
        val ctx = deviceManager.rustContextInternal
        val handle = heartbeatJobs[uuid]
        if (ctx != null && handle != null) {
            try {
                NativeCore.stopHeartbeatSender(ctx, handle)
            } catch (_: Exception) {}
        }
        heartbeatJobs.remove(uuid)
        heartbeatedDevices.remove(uuid)
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
        stopHeartbeatToDevice(uuid)

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
                val ctx = deviceManager.rustContextInternal
                if (ctx == null) return Pair(false, "未初始化")
                val batteryLevel = BatteryUtils.getBatteryLevel(deviceManager.contextInternal)
                val isCharging = BatteryUtils.isCharging(deviceManager.contextInternal)
                val battery = if (isCharging) batteryLevel else -batteryLevel
                val localIp = NativeCore.getLocalIp() ?: "0.0.0.0"
                val result = NativeCore.sendHandshake(ctx, deviceManager.uuid, deviceManager.localPublicKey, localIp, battery, "android")

                if (result == 0) {
                    delay(500)
                    if (deviceManager.isAuthenticatedInternal(device.uuid)) {
                        startHeartbeatToDevice(device.uuid, device.ip, device.port, "")
                        deviceManager.deviceLastSeenInternal[device.uuid] = System.currentTimeMillis()
                        try {
                            scope.launch { deviceManager.updateDeviceListInternal() }
                        } catch (_: Exception) {}

                        if (device.uuid != deviceManager.uuid) {
                            val myInfo = deviceManager.getDeviceInfoInternal(deviceManager.uuid)
                            if (myInfo != null && !heartbeatedDevices.contains(device.uuid)) {
                                deviceManager.connectToDevice(myInfo)
                            }
                        }
                        return Pair(true, null)
                    } else {
                        return Pair(false, "认证失败")
                    }
                } else {
                    return Pair(false, "连接失败")
                }
            } catch (e: UnsupportedOperationException) {
                val ctx = deviceManager.contextInternal
                val displayName = device.displayName
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(ctx, "设备 $displayName 使用旧版加密协议，请在对方设备上升级 NotifyRelay", android.widget.Toast.LENGTH_LONG).show()
                }
                Logger.e("死神-NotifyRelay", "connectToDevice 格式不匹配: ${e.message}")
                return Pair(false, e.message)
            } catch (e: Exception) {
                lastException = e
                if (retry < maxRetries - 1) {
                    delay(1000)
                }
            }
        }

        Logger.e("死神-NotifyRelay", "connectToDevice所有重试失败: ${lastException?.message}")
        return Pair(false, lastException?.message ?: "连接失败")
    }
}
