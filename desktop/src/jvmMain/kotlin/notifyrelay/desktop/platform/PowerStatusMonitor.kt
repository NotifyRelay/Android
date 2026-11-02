package notifyrelay.desktop.platform

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference
import notifyrelay.base.log.Logger

/** 本机电源状态快照：电量百分比（0-100）与是否充电。 */
internal data class PowerSnapshot(
    val percent: Int,
    val charging: Boolean,
)

/**
 * 电源状态变化监听。
 *
 * 与 Windows 端 `SystemInfoService.BatteryChanged` 同源：经 powrprof 的
 * `PowerSettingRegisterNotification` 以 `DEVICE_NOTIFY_CALLBACK` 注册回调
 * （电池剩余电量与交流电来源两个设置），系统在电量或电源变化时回调本进程，
 * 无需轮询也无需窗口句柄。
 *
 * 回调里重读一次 `GetSystemPowerStatus`（与 Windows 端 `OnBatteryChanged` 一致），
 * 再刷新 [snapshot] 并通知 [listener]。
 */
internal object PowerStatusMonitor {
    private const val TAG = "DesktopPower"

    /** 注册时按回调方式接收（DEVICE_NOTIFY_CALLBACK）。 */
    private const val DEVICE_NOTIFY_CALLBACK = 2

    /** Windows ERROR_SUCCESS。 */
    private const val ERROR_SUCCESS = 0

    private val snapshotState = mutableStateOf(PowerSnapshot(BatteryLevel.percent(), BatteryLevel.isCharging()))

    /** 当前电源状态（Compose 可观察，变化时驱动重组）。 */
    val snapshot: State<PowerSnapshot> = snapshotState

    /** 状态变化后的通知（用于把带符号电量推给 core 心跳调度器）。 */
    @Volatile
    private var listener: ((PowerSnapshot) -> Unit)? = null

    /** 保持回调与参数结构强引用，防止被 GC 回收导致系统侧悬空。 */
    private var callbackRef: DeviceNotifyCallback? = null
    private var parametersRef: DeviceNotifySubscribeParameters? = null
    private val registrationHandles = mutableListOf<Pointer>()

    private val powrProf: PowrProf? by lazy {
        if (!isWindows()) {
            return@lazy null
        }
        runCatching { Native.load("powrprof", PowrProf::class.java) }
            .onFailure { Logger.w(TAG, "加载 powrprof 失败，电源状态不监听变化", it) }
            .getOrNull()
    }

    /**
     * 注册电源状态变化监听；重复调用无副作用。
     *
     * @param onChanged 状态变化回调（在系统回调线程执行）
     */
    @Synchronized
    fun start(onChanged: (PowerSnapshot) -> Unit) {
        listener = onChanged
        if (registrationHandles.isNotEmpty()) return
        val library = powrProf ?: return
        val callback =
            object : DeviceNotifyCallback {
                override fun invoke(
                    context: Pointer?,
                    type: Int,
                    setting: Pointer?,
                ): Int {
                    refresh()
                    return ERROR_SUCCESS
                }
            }
        val parameters = DeviceNotifySubscribeParameters()
        parameters.setCallback(callback)
        callbackRef = callback
        parametersRef = parameters

        for (guid in MONITORED_SETTINGS) {
            val handle = PointerByReference()
            val code = library.PowerSettingRegisterNotification(guid, DEVICE_NOTIFY_CALLBACK, parameters.pointer, handle)
            if (code == ERROR_SUCCESS) {
                handle.value?.let { registrationHandles.add(it) }
            } else {
                Logger.w(TAG, "电源设置注册失败: code=" + code)
            }
        }
        if (registrationHandles.isEmpty()) {
            Logger.w(TAG, "电源状态监听注册失败，界面仅在重组时刷新")
        } else {
            Logger.i(TAG, "电源状态监听已注册: " + registrationHandles.size + " 项")
        }
    }

    /** 重读一次电源状态并触发通知。 */
    fun refresh() {
        val next = PowerSnapshot(BatteryLevel.percent(), BatteryLevel.isCharging())
        if (next == snapshotState.value) return
        snapshotState.value = next
        runCatching { listener?.invoke(next) }
            .onFailure { Logger.w(TAG, "电源状态回调失败", it) }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** 需要监听的电源设置：电池百分比与交流电来源。 */
    private val MONITORED_SETTINGS =
        listOf(
            // GUID_BATTERY_PERCENTAGE_REMAINING
            Guid(0xA7AD8041.toInt(), 0xB45A.toShort(), 0x4CAE.toShort(), byteArrayOf(0x87.toByte(), 0xA3.toByte(), 0xEE.toByte(), 0xCB.toByte(), 0xB4.toByte(), 0x68, 0xA9.toByte(), 0xE1.toByte())),
            // GUID_ACDC_POWER_SOURCE
            Guid(0x5D3E9A59, 0xE9D5.toShort(), 0x4B00.toShort(), byteArrayOf(0xA6.toByte(), 0xBD.toByte(), 0xFF.toByte(), 0x34.toByte(), 0xFF.toByte(), 0x51, 0x65, 0x48)),
        )

    /** powrprof 的电源设置通知接口。 */
    internal interface PowrProf : Library {
        fun PowerSettingRegisterNotification(
            settingGuid: Guid,
            flags: Int,
            recipient: Pointer,
            registrationHandle: PointerByReference,
        ): Int

        fun PowerSettingUnregisterNotification(registrationHandle: Pointer): Int
    }

    /** 系统的电源设置变化回调。 */
    internal interface DeviceNotifyCallback : Callback {
        fun invoke(
            context: Pointer?,
            type: Int,
            setting: Pointer?,
        ): Int
    }

    /** DEVICE_NOTIFY_SUBSCRIBE_PARAMETERS；字段布局与 Win32 一致。 */
    @Structure.FieldOrder("callback", "context")
    internal class DeviceNotifySubscribeParameters : Structure() {
        @JvmField
        var callback: DeviceNotifyCallback? = null

        @JvmField
        var context: Pointer? = null

        /** 写入回调函数指针并刷新内存布局。 */
        fun setCallback(value: DeviceNotifyCallback) {
            callback = value
            write()
        }
    }

    /** 注销全部电源设置监听；重复调用无副作用。 */
    @Synchronized
    fun stop() {
        val library = powrProf ?: return
        for (handle in registrationHandles) {
            runCatching { library.PowerSettingUnregisterNotification(handle) }
                .onFailure { Logger.w(TAG, "电源设置注销失败", it) }
        }
        registrationHandles.clear()
        callbackRef = null
        parametersRef = null
        listener = null
    }

    /** Win32 GUID；字段布局与规范一致（data1 4 字节 / data2、data3 各 2 字节 / data4 8 字节）。 */
    @Structure.FieldOrder("data1", "data2", "data3", "data4")
    internal class Guid(
        data1: Int,
        data2: Short,
        data3: Short,
        data4: ByteArray,
    ) : Structure() {
        @JvmField
        var data1: Int = data1

        @JvmField
        var data2: Short = data2

        @JvmField
        var data3: Short = data3

        @JvmField
        var data4: ByteArray = data4
    }
}
