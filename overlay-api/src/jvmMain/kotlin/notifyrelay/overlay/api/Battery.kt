package notifyrelay.overlay.api

/**
 * 电池设备快照。
 *
 * 字段与 `device-battery-lib` crate 的 `BatteryDeviceInfo` 一一对应。
 */
data class BatteryDevice(
    /** USB Vendor ID（0x046D = Logitech，0x37D7 = Flydigi）。 */
    val vendorId: Int,
    /** USB Product ID。 */
    val productId: Int,
    /** 设备稳定标识（VID:PID:槽位），用于刷新前后对比同一台设备。 */
    val deviceId: String,
    /** 人类可读设备名；可由用户在设置页覆盖。 */
    val deviceName: String,
    /** 接收器配对槽位：0 = 直连，1..6 = 接收器；飞智恒为 0。 */
    val slot: Int,
    /** 是否在线。 */
    val online: Boolean,
    /** 是否有电量数据。 */
    val hasBattery: Boolean,
    /** 电量百分比 0~100；无电量或离线为 -1。 */
    val batteryPercent: Int,
    /** 充电状态：0=放电 1=充电 2=慢充 3=已满 4=错误 5=未知。 */
    val statusRaw: Int,
) {
    /** 是否充电（充电与慢充均视为充电）。 */
    val isCharging: Boolean get() = statusRaw == 1 || statusRaw == 2
}

/**
 * 设备电池数据源。
 *
 * 实现侧经 `device_battery_lib.dll` 的 C FFI 枚举设备；枚举是阻塞 IO，由实现自行调度。
 */
interface BatterySource {
    /** 是否可枚举（原生库可用）。 */
    val available: Boolean

    /** 当前设备快照；空集合表示无设备。 */
    fun devices(): List<BatteryDevice>

    /** 启动后台轮询；已启动时幂等。 */
    fun start()

    /** 停止轮询；未启动时幂等。 */
    fun stop()

    /** 立即枚举一次，不改变监控状态。 */
    fun refreshNow()

    /**
     * 设置或清除设备自定义名。
     *
     * [name] 为空表示回退到 FFI 原始名；覆盖按 [BatteryDevice.deviceId] 持久化，重启后保留。
     */
    fun setDeviceNameOverride(
        deviceId: String,
        name: String?,
    )

    /**
     * 从列表中移除一台设备，并清除它的自定义名覆盖。
     *
     * 用于清理长期离线、重新枚举也不会再出现的残留条目；对在线设备同样可用。
     */
    fun removeDevice(deviceId: String)

    /** 清除全部设备名覆盖，回退到原始名。 */
    fun clearDeviceNameOverrides()

    /** 设备集合变化订阅；返回取消订阅函数。 */
    fun observe(listener: (List<BatteryDevice>) -> Unit): AutoCloseable
}
