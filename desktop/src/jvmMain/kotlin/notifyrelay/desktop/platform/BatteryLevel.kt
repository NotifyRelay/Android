package notifyrelay.desktop.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Structure
import notifyrelay.base.log.Logger

/**
 * 桌面端电量读取。
 *
 * 与 Windows 端 SystemInfoService 同源：经 kernel32 的 GetSystemPowerStatus 取电池剩余百分比；
 * 非 Windows、无电池（BatteryLifePercent 为 255 未知）或读取异常时回落到 100，
 * 与 Windows 端与 Android 端各分支的默认值保持一致。
 */
internal object BatteryLevel {
    private const val TAG = "DesktopBattery"

    /** 取不到电量时的回落值。 */
    private const val FALLBACK_PERCENT = 100

    /** BatteryLifePercent 的未知取值。 */
    private const val UNKNOWN_PERCENT = 255

    private val kernel32: Kernel32? by lazy {
        if (!isWindows()) {
            return@lazy null
        }
        runCatching { Native.load("kernel32", Kernel32::class.java) }
            .onFailure { Logger.w(TAG, "加载 kernel32 失败，电量回落默认值", it) }
            .getOrNull()
    }

    /** 电量百分比（0-100）。 */
    fun percent(): Int {
        val library = kernel32 ?: return FALLBACK_PERCENT
        return runCatching {
            val status = SystemPowerStatus()
            if (!library.GetSystemPowerStatus(status)) return@runCatching FALLBACK_PERCENT
            status.read()
            val percent = status.batteryLifePercent.toInt() and 0xFF
            if (percent == UNKNOWN_PERCENT) FALLBACK_PERCENT else percent.coerceIn(0, 100)
        }.getOrElse {
            Logger.w(TAG, "读取电量失败，回落默认值", it)
            FALLBACK_PERCENT
        }
    }

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
