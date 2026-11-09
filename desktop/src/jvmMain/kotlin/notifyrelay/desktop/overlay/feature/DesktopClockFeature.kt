package notifyrelay.desktop.overlay.feature

import notifyrelay.desktop.overlay.DesktopOverlayStore
import notifyrelay.desktop.platform.DesktopOverlaySettings
import notifyrelay.overlay.api.ClockConfig

/**
 * 时间扩展点：封装 [DesktopClockSource] 的生命周期与数据订阅。
 *
 * 时间格式化在 JVM 内完成（`java.time.LocalTime`），不使用 JNA。
 * 启停骨架见 [DesktopOverlayFeature]。
 */
class DesktopClockFeature(
    private val source: DesktopClockSource =
        DesktopClockSource(
            ClockConfig(
                showSeconds = DesktopOverlaySettings.clockShowSeconds,
                use24Hour = DesktopOverlaySettings.clockUse24Hour,
            ),
        ),
) : DesktopOverlayFeature(ID) {
    init {
        register()
    }

    override fun beforeSubscribe() {
        source.updateConfig(currentConfig())
    }

    override fun subscribe(): AutoCloseable = source.observe { text -> DesktopOverlayStore.putClockText(text) }

    override fun startSource() {
        if (DesktopOverlaySettings.clockEnabled) source.start()
    }

    override fun stopSource() = source.stop()

    override fun clearSink() {
        DesktopOverlayStore.clockText.value = ""
    }

    /** 设置变更后同步显示格式与刷新节奏。 */
    fun syncConfig() {
        source.updateConfig(currentConfig())
    }

    private fun currentConfig(): ClockConfig =
        ClockConfig(
            showSeconds = DesktopOverlaySettings.clockShowSeconds,
            use24Hour = DesktopOverlaySettings.clockUse24Hour,
        )

    companion object {
        const val ID = "clock"
    }
}
