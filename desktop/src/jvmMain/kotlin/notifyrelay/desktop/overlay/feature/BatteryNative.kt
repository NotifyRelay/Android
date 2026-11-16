package notifyrelay.desktop.overlay.feature

import notifyrelay.base.log.Logger
import notifyrelay.battery.ffi.BatteryDeviceInfo
import notifyrelay.battery.ffi.BatteryLevelKind
import notifyrelay.battery.ffi.BatteryLib
import notifyrelay.battery.ffi.ChargeStatus
import notifyrelay.overlay.api.BatteryDevice

/**
 * 电池设备枚举门面。
 *
 * 经 UniFFI 生成的 [BatteryLib] 绑定访问原生库；枚举本身是阻塞的 HID IO
 * （每次可能耗时数百毫秒），调用方须在后台线程使用。
 * 原生库不可用时 [available] 为 false，各方法返回空结果，不抛异常。
 */
internal object BatteryNative {
    private const val TAG = "BatteryNative"

    private val library: BatteryLib? by lazy {
        runCatching { BatteryLib() }
            .onFailure { Logger.w(TAG, "原生库加载失败: " + it.message) }
            .getOrNull()
    }

    /** 原生库是否可用。 */
    val available: Boolean get() = library != null

    /**
     * 枚举全部电池设备。
     *
     * @return 设备列表；库不可用或枚举失败时返回空列表
     */
    suspend fun enumerate(): List<BatteryDevice> {
        val lib = library ?: return emptyList()
        val snapshot =
            runCatching { lib.enumerateDevices() }.getOrElse {
                Logger.w(TAG, "枚举设备失败", it)
                return emptyList()
            }
        return snapshot.devices.mapNotNull { info ->
            runCatching { info.toDomain() }
                .onFailure { Logger.w(TAG, "设备快照转换失败", it) }
                .getOrNull()
        }
    }
}

/** 绑定记录到契约模型的转换：无电量或离线时电量记 -1。 */
private fun BatteryDeviceInfo.toDomain(): BatteryDevice {
    val percent = if (hasBattery && online) percentage.toInt() and 0xFF else -1
    return BatteryDevice(
        vendorId = vendorId.toInt() and 0xFFFF,
        productId = productId.toInt() and 0xFFFF,
        deviceId = deviceId,
        deviceName = name.ifBlank { deviceId },
        slot = slot.toInt() and 0xFF,
        online = online,
        hasBattery = hasBattery,
        batteryPercent = percent,
        statusRaw = status.rawValue(),
    )
}

/** 充电状态的线协议取值，与 `BatteryDevice.statusRaw` 的约定一致。 */
internal fun ChargeStatus.rawValue(): Int =
    when (this) {
        ChargeStatus.DISCHARGING -> 0
        ChargeStatus.CHARGING -> 1
        ChargeStatus.CHARGING_SLOW -> 2
        ChargeStatus.FULL -> 3
        ChargeStatus.ERROR -> 4
        ChargeStatus.UNKNOWN -> 5
    }

/** 电量等级的线协议取值。 */
internal fun BatteryLevelKind.rawValue(): Int =
    when (this) {
        BatteryLevelKind.CRITICAL -> 0
        BatteryLevelKind.LOW -> 1
        BatteryLevelKind.GOOD -> 2
        BatteryLevelKind.FULL -> 3
        BatteryLevelKind.UNKNOWN -> 4
    }
