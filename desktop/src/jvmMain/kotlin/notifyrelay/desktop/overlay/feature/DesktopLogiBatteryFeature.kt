package notifyrelay.desktop.overlay.feature

import notifyrelay.desktop.overlay.DesktopOverlayStore
import notifyrelay.desktop.platform.DesktopOverlaySettings
import notifyrelay.overlay.api.OverlayFeature
import notifyrelay.overlay.api.OverlayFeatureRegistry
import java.io.Closeable

/**
 * 罗技电池扩展点：封装 [DesktopLogiBatterySource] 的生命周期与数据订阅。
 *
 * 实现 [OverlayFeature] 并注册到 [notifyrelay.overlay.api.OverlayFeatureRegistry]。
 */
class DesktopLogiBatteryFeature(
    val source: DesktopLogiBatterySource = DesktopLogiBatterySource(),
) : OverlayFeature,
    Closeable {
    override val id = ID
    override val available = source.available

    private var subscription: AutoCloseable? = null

    @Volatile
    private var started = false

    init {
        OverlayFeatureRegistry.register(this)
    }

    override fun start() {
        if (started) return
        started = true
        subscription = source.observe { devices -> DesktopOverlayStore.putLogiDevices(devices) }
        if (DesktopOverlaySettings.logiEnabled) source.start()
    }

    override fun stop() {
        if (!started) return
        started = false
        source.stop()
        runCatching { subscription?.close() }
        subscription = null
        DesktopOverlayStore.logiDevices.value = emptyList()
    }

    override fun syncSwitch(enabled: Boolean) {
        if (enabled) source.start() else source.stop()
    }

    override fun close() {
        stop()
        OverlayFeatureRegistry.unregister(id)
    }

    companion object {
        const val ID = "logi_battery"
    }
}
