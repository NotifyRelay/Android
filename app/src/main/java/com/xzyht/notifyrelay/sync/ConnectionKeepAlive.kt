package com.xzyht.notifyrelay.sync

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import notifyrelay.base.util.Logger
import notifyrelay.core.util.BatteryUtils
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.nativecore.NativeCore
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 连接保活与重连策略封装：
 *
 * - 对「已经成功握手并建立信任」的设备：
 *   - 心跳由 Rust 统一心跳调度器自动管理（known_devices + 配对状态），
 *     本类不再维护每设备心跳任务；
 *
 * - 同时，这里还承接「首次连接」时的握手重试逻辑：
 *   - performDeviceConnectionWithRetry 统一处理握手重试 + 认证成功后的状态更新，
 *   - 让 DeviceConnectionManager.connectToDevice 变成一个较薄的入口。
 *
 * 注意：本类只通过 DeviceConnectionManager 暴露的 internal 视图读写状态，
 * 不直接操作其 private 字段，保持边界清晰。
 */
class ConnectionKeepAlive(
    private val deviceManager: DeviceConnectionManager,
    private val scope: CoroutineScope
) {

    /**
     * 封装设备连接握手与重试逻辑：
     * - 最多重试 maxRetries 次
     * - 认证成功后更新 AuthInfo/deviceInfoCache（心跳由 Rust 调度器接管）
     * - 失败原因通过 (Boolean, String?) 形式返回
     */
    suspend fun performDeviceConnectionWithRetry(device: DeviceInfo, maxRetries: Int): Pair<Boolean, String?> {
        var lastException: Exception? = null

        for (retry in 0 until maxRetries) {
            var deferred: kotlinx.coroutines.CompletableDeferred<Boolean>? = null
            try {
                val ctx = deviceManager.rustContextInternal
                if (ctx == null) return Pair(false, "未初始化")
                val batteryLevel = BatteryUtils.getBatteryLevel(deviceManager.contextInternal)
                val isCharging = BatteryUtils.isCharging(deviceManager.contextInternal)
                val battery = if (isCharging) batteryLevel else -batteryLevel
                val localIp = NativeCore.getLocalIp() ?: "0.0.0.0"
                deferred = deviceManager.registerHandshakeWaiter(device.uuid)
                val sendOk = NativeCore.sendHandshake(ctx, deviceManager.uuid, deviceManager.localPublicKey, localIp, device.ip, battery, "android")

                if (sendOk == 0) {
                    val handshakeResult = withTimeoutOrNull(5000) { deferred.await() }
                    when (handshakeResult) {
                        true -> {
                            try {
                                scope.launch { deviceManager.updateDeviceListInternal() }
                            } catch (_: Exception) {}
                            return Pair(true, null)
                        }
                        false -> {
                            lastException = UnsupportedOperationException("对方拒绝连接")
                            if (retry < maxRetries - 1) delay(1000)
                        }
                        null -> {
                            lastException = UnsupportedOperationException("握手超时")
                            if (retry < maxRetries - 1) delay(1000)
                        }
                    }
                } else {
                    lastException = UnsupportedOperationException("发送握手失败")
                    if (retry < maxRetries - 1) delay(1000)
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
            } finally {
                deferred?.let { deviceManager.cancelHandshakeWaiter(device.uuid, it) }
            }
        }

        Logger.e("死神-NotifyRelay", "connectToDevice所有重试失败: ${lastException?.message}")
        return Pair(false, lastException?.message ?: "连接失败")
    }
}
