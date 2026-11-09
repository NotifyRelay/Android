package notifyrelay.desktop.overlay.feature

import notifyrelay.desktop.overlay.DesktopOverlayStore
import notifyrelay.desktop.platform.DesktopOverlaySettings

/**
 * 罗技电池扩展点：封装 [DesktopLogiBatterySource] 的生命周期与数据订阅。
 *
 * 启停骨架见 [DesktopOverlayFeature]。
 */
class DesktopLogiBatteryFeature(
    val source: DesktopLogiBatterySource = DesktopLogiBatterySource(),
) : DesktopOverlayFeature(ID) {
    override val available: Boolean get() = source.available

    init {
        register()
    }

    override fun subscribe(): AutoCloseable = source.observe { devices -> DesktopOverlayStore.putLogiDevices(devices) }

    override fun startSource() {
        if (DesktopOverlaySettings.logiEnabled) source.start()
    }

    override fun stopSource() = source.stop()

    override fun clearSink() {
        DesktopOverlayStore.logiDevices.value = emptyList()
    }

    companion object {
        const val ID = "logi_battery"
    }
}
