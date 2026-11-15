package notifyrelay.desktop.overlay.feature

import notifyrelay.desktop.overlay.DesktopOverlayStore
import notifyrelay.desktop.platform.DesktopOverlaySettings

/**
 * 设备电池扩展点：封装 [DesktopBatterySource] 的生命周期与数据订阅。
 *
 * 启停骨架见 [DesktopOverlayFeature]。
 */
class DesktopBatteryFeature(
    val source: DesktopBatterySource = DesktopBatterySource(),
) : DesktopOverlayFeature(ID) {
    override val available: Boolean get() = source.available

    init {
        register()
    }

    override fun subscribe(): AutoCloseable = source.observe { devices -> DesktopOverlayStore.putBatteryDevices(devices) }

    override fun startSource() {
        if (DesktopOverlaySettings.batteryEnabled) source.start()
    }

    override fun stopSource() = source.stop()

    override fun clearSink() {
        DesktopOverlayStore.batteryDevices.value = emptyList()
    }

    companion object {
        const val ID = "battery"
    }
}
