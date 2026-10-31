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
import notifyrelay.overlay.api.LogiBatteryDevice
import notifyrelay.overlay.api.LogiBatterySource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 罗技电池数据源。
 *
 * 经 [LogiBatteryNative] 枚举设备；枚举是阻塞的 HID IO，故在后台协程按固定间隔轮询，
 * 结果与上次一致时不再广播（避免无谓的界面重组）。
 * 用户自定义设备名按 `设备标识` 覆盖 FFI 原始名，持久化到设置并即时应用。
 */
class DesktopLogiBatterySource(
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
) : LogiBatterySource {
    private companion object {
        const val TAG = "DesktopLogiBattery"

        /** 轮询间隔：枚举含逐个设备的 HID 探测，间隔过短会持续占用 USB 通道。 */
        const val DEFAULT_INTERVAL_MS = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val listeners = CopyOnWriteArrayList<(List<LogiBatteryDevice>) -> Unit>()

    private val running = AtomicBoolean(false)

    private var pollJob: Job? = null

    /** 用户自定义设备名：键为设备标识。 */
    private val nameOverrides = ConcurrentHashMap<String, String>()

    @Volatile
    private var current: List<LogiBatteryDevice> = emptyList()

    init {
        runCatching { nameOverrides.putAll(DesktopOverlaySettings.logiDeviceNameOverrides) }
            .onFailure { Logger.w(TAG, "恢复设备名覆盖失败", it) }
    }

    override val available: Boolean get() = LogiBatteryNative.available

    override fun devices(): List<LogiBatteryDevice> = current

    /** 启动轮询；原生库不可用时不启动。 */
    override fun start() {
        if (!available) {
            Logger.d(TAG, "原生库不可用，不启动罗技设备轮询")
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
        DesktopOverlaySettings.logiDeviceNameOverrides = nameOverrides.toMap()

        // 不重新枚举，直接把覆盖应用到当前快照并广播
        val updated = applyOverrides(current)
        if (updated != current) {
            current = updated
            notifyListeners()
        }
    }

    override fun observe(listener: (List<LogiBatteryDevice>) -> Unit): AutoCloseable {
        listeners.add(listener)
        listener(current)
        return AutoCloseable { listeners.remove(listener) }
    }

    /** 枚举一次并按需广播。 */
    private suspend fun refresh() {
        val devices =
            runCatching { LogiBatteryNative.enumerate() }.getOrElse {
                Logger.w(TAG, "枚举罗技设备失败", it)
                return
            }
        val merged = applyOverrides(devices)
        if (merged == current) return
        current = merged
        Logger.i(TAG, "罗技设备快照已更新，共 " + merged.size + " 台")
        notifyListeners()
    }

    /** 把用户自定义名应用到设备列表；无覆盖时原样返回。 */
    private fun applyOverrides(devices: List<LogiBatteryDevice>): List<LogiBatteryDevice> {
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
