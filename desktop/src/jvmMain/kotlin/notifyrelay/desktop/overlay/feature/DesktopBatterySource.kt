package notifyrelay.desktop.overlay.feature

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import notifyrelay.base.log.Logger
import notifyrelay.desktop.platform.DesktopOverlaySettings
import notifyrelay.overlay.api.BatteryDevice
import notifyrelay.overlay.api.BatterySource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 设备电池数据源。
 *
 * 经 [BatteryNative] 枚举设备；枚举是阻塞的 HID IO，故在后台协程按固定间隔轮询，
 * 结果与上次一致时不再广播（避免无谓的界面重组）。
 * 用户自定义设备名按 `设备标识` 覆盖 FFI 原始名，持久化到设置并即时应用。
 */
class DesktopBatterySource(
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
) : BatterySource {
    private companion object {
        const val TAG = "DesktopBattery"

        /** 轮询间隔：枚举含逐个设备的 HID 探测，间隔过短会持续占用 USB 通道。 */
        const val DEFAULT_INTERVAL_MS = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val listeners = CopyOnWriteArrayList<(List<BatteryDevice>) -> Unit>()

    private val running = AtomicBoolean(false)

    private var pollJob: Job? = null

    /** 用户自定义设备名：键为设备标识。 */
    private val nameOverrides = ConcurrentHashMap<String, String>()

    @Volatile
    private var current: List<BatteryDevice> = emptyList()

    init {
        runCatching { nameOverrides.putAll(DesktopOverlaySettings.batteryDeviceNameOverrides) }
            .onFailure { Logger.w(TAG, "恢复设备名覆盖失败", it) }
    }

    override val available: Boolean get() = BatteryNative.available

    override fun devices(): List<BatteryDevice> = current

    /** 启动轮询；原生库不可用时不启动。 */
    override fun start() {
        if (!available) {
            Logger.d(TAG, "原生库不可用，不启动电池设备轮询")
            return
        }
        if (!running.compareAndSet(false, true)) return
        pollJob =
            scope.launch {
                while (isActive) {
                    refresh()
                    delay(intervalMs)
                }
            }
    }

    /** 停止轮询。 */
    override fun stop() {
        if (!running.compareAndSet(true, false)) return
        pollJob?.cancel()
        pollJob = null
    }

    /** 立即枚举一次，不改变监控状态。 */
    override fun refreshNow() {
        scope.launch { refresh() }
    }

    /**
     * 设置或清除设备自定义名。
     *
     * 空名表示回退 FFI 原始名；覆盖即时应用到当前快照并持久化，不重新枚举。
     */
    override fun setDeviceNameOverride(
        deviceId: String,
        name: String?,
    ) {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            nameOverrides.remove(deviceId)
        } else {
            nameOverrides[deviceId] = trimmed
        }
        DesktopOverlaySettings.batteryDeviceNameOverrides = nameOverrides.toMap()

        // 不重新枚举，直接把覆盖应用到当前快照并广播
        val updated = applyOverrides(current)
        if (updated != current) {
            current = updated
            notifyListeners()
        }
    }

    override fun removeDevice(deviceId: String) {
        val hadOverride = nameOverrides.remove(deviceId) != null
        if (hadOverride) {
            DesktopOverlaySettings.batteryDeviceNameOverrides = nameOverrides.toMap()
        }
        val updated = current.filterNot { it.deviceId == deviceId }
        if (updated.size != current.size) {
            current = updated
            notifyListeners()
        }
    }

    override fun clearDeviceNameOverrides() {
        if (nameOverrides.isEmpty()) return
        nameOverrides.clear()
        DesktopOverlaySettings.batteryDeviceNameOverrides = emptyMap()
        val restored = applyOverrides(current)
        if (restored != current) {
            current = restored
            notifyListeners()
        }
    }

    override fun observe(listener: (List<BatteryDevice>) -> Unit): AutoCloseable {
        listeners.add(listener)
        listener(current)
        return AutoCloseable { listeners.remove(listener) }
    }

    /** 枚举一次并按需广播。 */
    private suspend fun refresh() {
        val devices =
            runCatching { BatteryNative.enumerate() }.getOrElse {
                Logger.w(TAG, "枚举电池设备失败", it)
                return
            }
        val merged = applyOverrides(devices)
        if (merged == current) return
        current = merged
        Logger.i(TAG, "电池设备快照已更新，共 " + merged.size + " 台")
        notifyListeners()
    }

    /** 把用户自定义名应用到设备列表；无覆盖时原样返回。 */
    private fun applyOverrides(devices: List<BatteryDevice>): List<BatteryDevice> {
        if (nameOverrides.isEmpty()) return devices
        return devices.map { device ->
            val custom = nameOverrides[device.deviceId]
            if (custom.isNullOrBlank() || custom == device.deviceName) device else device.copy(deviceName = custom)
        }
    }

    private fun notifyListeners() {
        val snapshot = current
        listeners.forEach { listener ->
            runCatching { listener(snapshot) }
                .onFailure { Logger.w(TAG, "设备快照回调失败", it) }
        }
    }
}
