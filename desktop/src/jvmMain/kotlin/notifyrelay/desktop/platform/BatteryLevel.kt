package notifyrelay.desktop.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Structure
import notifyrelay.base.log.Logger

/**
 * 桌面端电源状态读取。
 *
 * 与 Windows 端 SystemInfoService 同源：经 kernel32 的 GetSystemPowerStatus 取
 * 电池剩余百分比与充电状态；非 Windows、无电池或读取异常时回落默认值
 * （电量 100、未充电），与 Windows 端各分支的默认值保持一致。
 */
internal object BatteryLevel {
    private const val TAG = "DesktopBattery"

    /** 取不到电量时的回落值。 */
    private const val FALLBACK_PERCENT = 100

    /** BatteryLifePercent 的未知取值。 */
    private const val UNKNOWN_PERCENT = 255

    /** ACLineStatus 的「交流电在线」取值。 */
    private const val AC_LINE_ONLINE = 1

    /** BatteryFlag 的「充电中」位。 */
    private const val BATTERY_FLAG_CHARGING = 0x08

    private val kernel32: Kernel32? by lazy {
        if (!isWindows()) {
            return@lazy null
        }
        runCatching { Native.load("kernel32", Kernel32::class.java) }
            .onFailure { Logger.w(TAG, "加载 kernel32 失败，电源状态回落默认值", it) }
            .getOrNull()
    }

    /** 电量百分比（0-100，无符号；供界面展示）。 */
    fun percent(): Int = readStatus()?.batteryPercent() ?: FALLBACK_PERCENT

    /** 是否处于充电状态：交流电在线或电池正在充电。 */
    fun isCharging(): Boolean = readStatus()?.isCharging() ?: false

    /**
     * 带符号电量：正=充电、负=放电（core 的 [battery]/[isCharging] 以符号区分，见 device_registry）。
     *
     * 上报 core 的启动参数、周期广播、配对应答与心跳调度器参数一律用本方法。
     */
    fun signedPercent(): Int {
        val level = readStatus() ?: return -FALLBACK_PERCENT
        return if (level.isCharging()) level.batteryPercent() else -level.batteryPercent()
    }

    /** 读取一次电源状态；非 Windows、接口失败或异常时返回 null。 */
    private fun readStatus(): SystemPowerStatus? {
        val library = kernel32 ?: return null
        return runCatching {
            val status = SystemPowerStatus()
            if (!library.GetSystemPowerStatus(status)) return@runCatching null
            status.read()
            status
        }.getOrElse {
            Logger.w(TAG, "读取电源状态失败，回落默认值", it)
            null
        }
    }

    /** 剩余百分比；未知取值（255）回落默认值。 */
    private fun SystemPowerStatus.batteryPercent(): Int {
        val percent = batteryLifePercent.toInt() and 0xFF
        return if (percent == UNKNOWN_PERCENT) FALLBACK_PERCENT else percent.coerceIn(0, 100)
    }

    /** 交流电在线，或电池状态含「充电中」位。 */
    private fun SystemPowerStatus.isCharging(): Boolean = (acLineStatus.toInt() and 0xFF) == AC_LINE_ONLINE || (batteryFlag.toInt() and BATTERY_FLAG_CHARGING) != 0

    private fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** kernel32 的电源状态接口。 */
    internal interface Kernel32 : Library {
        fun GetSystemPowerStatus(powerStatus: SystemPowerStatus): Boolean
    }

    /** Win32 的 SYSTEM_POWER_STATUS 结构体（字段顺序与声明一致）。 */
    @Structure.FieldOrder("acLineStatus", "batteryFlag", "batteryLifePercent", "reserved1", "batteryLifeTime", "batteryFullLifeTime")
    internal class SystemPowerStatus : Structure() {
        @JvmField
        var acLineStatus: Byte = 0

        @JvmField
        var batteryFlag: Byte = 0

        @JvmField
        var batteryLifePercent: Byte = 0

        @JvmField
        var reserved1: Byte = 0

        @JvmField
        var batteryLifeTime: Int = 0

        @JvmField
        var batteryFullLifeTime: Int = 0
    }
}
