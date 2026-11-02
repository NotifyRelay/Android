package notifyrelay.desktop.overlay.feature

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import notifyrelay.base.log.Logger
import notifyrelay.overlay.api.LogiBatteryDevice

/**
 * `logi_battery.dll` 的 JNA 声明。
 *
 * 结构体字段与 Rust 侧 `LbDeviceInfo` / `LbDeviceList` 逐字段对应，
 * 字段顺序不可改动：JNA 按 [Structure.FieldOrder] 计算偏移与结构体大小。
 *
 * 库由 `:desktop:logiBatteryBuild` 构建，产物目录已加入 `jna.library.path`。
 */
internal interface LogiBatteryLibrary : Library {
    /** 枚举全部罗技 HID++ 设备及其电量；成功返回 0，负数表示错误。 */
    fun lb_enumerate_devices(out: LbDeviceList): Int

    /** 重新读取指定下标设备的电量；成功返回 0。 */
    fun lb_read_battery(
        index: Int,
        out: LbBatteryInfo,
    ): Int

    /** 释放 `lb_enumerate_devices` 分配的数组。 */
    fun lb_free_devices(list: LbDeviceList)

    /** 最近一次调用的错误信息（NUL 结尾 UTF-8）。 */
    fun lb_last_error(): Pointer
}

/**
 * Rust `LbDeviceInfo`。
 *
 * 对齐必须固定为 1：Rust 侧 `#[repr(C)]` 的最大对齐字段是 `u16`（对齐 2），
 * 结构体大小为 266；而 JNA 默认按平台对齐（x64 为 8）会算成 272，
 * 使数组步长错位，从第二台设备起读到错位数据。
 */
@Structure.FieldOrder("vendorId", "productId", "name", "slot", "online", "hasBattery", "percentage", "level", "status")
internal class LbDeviceInfo : Structure(ALIGN_NONE) {
    @JvmField
    var vendorId: Short = 0

    @JvmField
    var productId: Short = 0

    @JvmField
    var name: ByteArray = ByteArray(NAME_LENGTH)

    @JvmField
    var slot: Byte = 0

    @JvmField
    var online: Byte = 0

    @JvmField
    var hasBattery: Byte = 0

    @JvmField
    var percentage: Byte = 0

    @JvmField
    var level: Byte = 0

    @JvmField
    var status: Byte = 0

    /** 设备名 NUL 结尾，取到首个 0 为止。 */
    fun deviceName(): String {
        val end = name.indexOfFirst { it == 0.toByte() }
        val bytes = if (end < 0) name else name.copyOf(end)
        return bytes.toString(Charsets.UTF_8)
    }

    /**
     * 设备稳定标识：`VID:PID:槽位`，与 Windows 侧 `LogiBatteryDeviceInfo.DeviceId` 同口径。
     *
     * 十六进制小写、无前导零填充差异，供刷新前后比对同一台设备。
     */
    fun deviceId(): String = (u16(vendorId).toString(16) + ":" + u16(productId).toString(16) + ":" + u8(slot))

    private companion object {
        /** Rust 侧 `name: [u8; 256]`。 */
        const val NAME_LENGTH = 256

        /** 无符号 16 位读取。 */
        fun u16(value: Short): Int = value.toInt() and 0xFFFF

        /** 无符号 8 位读取。 */
        fun u8(value: Byte): Int = value.toInt() and 0xFF
    }
}

/** Rust `LbDeviceList`：两个字段为指针与 `i32`，对齐取平台默认即可。 */
@Structure.FieldOrder("devices", "count")
internal class LbDeviceList : Structure() {
    @JvmField
    var devices: Pointer? = null

    @JvmField
    var count: Int = 0
}

/** Rust `LbBatteryInfo`：三个 `u8`，对齐固定为 1。 */
@Structure.FieldOrder("percentage", "level", "status")
internal class LbBatteryInfo : Structure(ALIGN_NONE) {
    @JvmField
    var percentage: Byte = 0

    @JvmField
    var level: Byte = 0

    @JvmField
    var status: Byte = 0
}

/**
 * 罗技设备枚举门面。
 *
 * 枚举本身是阻塞的 HID IO（每次可能耗时数百毫秒），调用方须在后台线程使用；
 * 原生库不可用时 [available] 为 false，各方法返回空结果，不抛异常。
 */
internal object LogiBatteryNative {
    private const val TAG = "LogiBattery"

    /** 库名（`cargo build --release` 产出 `logi_battery.dll`）。 */
    private const val LIBRARY_NAME = "logi_battery"

    private val library: LogiBatteryLibrary? by lazy {
        runCatching { Native.load(LIBRARY_NAME, LogiBatteryLibrary::class.java) }
            .onFailure { Logger.w(TAG, "原生库加载失败: " + it.message) }
            .getOrNull()
    }

    /** 原生库是否可用。 */
    val available: Boolean get() = library != null

    /**
     * 枚举全部罗技设备。
     *
     * 内部完成「分配 → 逐个读取 → 释放」全过程，不把裸指针交给调用方。
     *
     * @return 设备列表；库不可用或枚举失败时返回空列表
     */
    fun enumerate(): List<LogiBatteryDevice> {
        val lib = library ?: return emptyList()
        val list = LbDeviceList()
        val result =
            runCatching { lib.lb_enumerate_devices(list) }.getOrElse {
                Logger.w(TAG, "枚举设备异常", it)
                return emptyList()
            }
        if (result != 0) {
            Logger.w(TAG, "枚举设备失败: " + lastError(lib) + " 代码=" + result)
            return emptyList()
        }

        return try {
            readDevices(lib, list)
        } finally {
            runCatching { lib.lb_free_devices(list) }
                .onFailure { Logger.w(TAG, "释放设备数组失败", it) }
        }
    }

    /** 按结构体大小逐元素读取数组。 */
    private fun readDevices(
        lib: LogiBatteryLibrary,
        list: LbDeviceList,
    ): List<LogiBatteryDevice> {
        val base = list.devices ?: return emptyList()
        if (list.count <= 0) return emptyList()

        val stride = LbDeviceInfo().size().toLong()
        return (0 until list.count).mapNotNull { index ->
            runCatching {
                // 用 newInstance 绑定指针：Structure.useMemory 为 protected，不对外可用
                val info = Structure.newInstance(LbDeviceInfo::class.java, base.share(index * stride))
                info.read()
                info.toDomain()
            }.getOrElse {
                Logger.w(TAG, "读取第 " + index + " 个设备失败", it)
                null
            }
        }
    }

    /** 读取最近一次错误信息；无错误记录或空指针时返回空串。 */
    private fun lastError(lib: LogiBatteryLibrary): String =
        runCatching {
            val pointer = lib.lb_last_error()
            // JNA 把返回的指针映射为非空类型，但原生侧可能返回空指针，需按地址判定
            if (Pointer.nativeValue(pointer) == 0L) return ""
            pointer.getString(0, Charsets.UTF_8.name())
        }.getOrDefault("")

    /** Rust 结构体到契约模型的转换：无电量或离线时电量记 -1。 */
    private fun LbDeviceInfo.toDomain(): LogiBatteryDevice {
        val onlineFlag = online.toInt() and 0xFF == 1
        val hasBatteryFlag = hasBattery.toInt() and 0xFF == 1
        val percent = if (hasBatteryFlag && onlineFlag) percentage.toInt() and 0xFF else -1
        val name = deviceName()
        return LogiBatteryDevice(
            vendorId = vendorId.toInt() and 0xFFFF,
            productId = productId.toInt() and 0xFFFF,
            deviceId = deviceId(),
            deviceName = name.ifBlank { deviceId() },
            slot = slot.toInt() and 0xFF,
            online = onlineFlag,
            hasBattery = hasBatteryFlag,
            batteryPercent = percent,
            statusRaw = status.toInt() and 0xFF,
        )
    }
}
