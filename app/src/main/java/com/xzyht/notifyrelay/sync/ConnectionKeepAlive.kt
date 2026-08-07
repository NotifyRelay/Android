package com.xzyht.notifyrelay.sync

import notifyrelay.base.util.Logger
import notifyrelay.core.util.BatteryUtils
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.nativecore.NativeCore
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
     * - 重试与超时由 Rust `nrc_connect_device` 内部完成（3次/5s超时/1s间隔）
     * - 认证成功后更新 AuthInfo/deviceInfoCache（心跳由 Rust 调度器接管）
     * - 失败原因通过 (Boolean, String?) 形式返回
     */
    suspend fun performDeviceConnectionWithRetry(device: DeviceInfo, maxRetries: Int): Pair<Boolean, String?> {
        val ctx = deviceManager.rustContextInternal
        if (ctx == null) return Pair(false, "未初始化")
        val batteryLevel = BatteryUtils.getBatteryLevel(deviceManager.contextInternal)
        val isCharging = BatteryUtils.isCharging(deviceManager.contextInternal)
        val battery = if (isCharging) batteryLevel else -batteryLevel

        val result = NativeCore.connectDevice(ctx, device.uuid, device.ip, battery, "android")
        if (result == 0) {
            try {
                scope.launch { deviceManager.updateDeviceListInternal() }
            } catch (_: Exception) {}
            return Pair(true, null)
        }
        Logger.e("死神-NotifyRelay", "connectToDevice 连接失败: ${device.displayName}, result=$result")
        return Pair(false, if (result < 0) "连接失败" else "连接被拒绝")
    }
}
