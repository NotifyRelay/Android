package notifyrelay.desktop.overlay.feature

import notifyrelay.desktop.overlay.DesktopOverlayStore
import notifyrelay.desktop.platform.DesktopOverlaySettings
import notifyrelay.overlay.api.ClockConfig
import notifyrelay.overlay.api.OverlayFeature
import notifyrelay.overlay.api.OverlayFeatureRegistry
import java.io.Closeable

/**
 * 时间扩展点：封装 [DesktopClockSource] 的生命周期与数据订阅。
 *
 * 时间完全在 JVM 内完成（`java.time.LocalTime`），不使用 JNA。
 * 实现 [OverlayFeature] 并注册到 [notifyrelay.overlay.api.OverlayFeatureRegistry]。
 */
class DesktopClockFeature(
    val source: DesktopClockSource =
        DesktopClockSource(
            ClockConfig(
                showSeconds = DesktopOverlaySettings.clockShowSeconds,
                use24Hour = DesktopOverlaySettings.clockUse24Hour,
            ),
        ),
) : OverlayFeature,
    Closeable {
    override val id = ID
    override val available = true

    private var subscription: AutoCloseable? = null

    @Volatile
    private var started = false

    init {
        OverlayFeatureRegistry.register(this)
    }

    override fun start() {
        if (started) return
        started = true
        source.updateConfig(currentConfig())
        subscription = source.observe { text -> DesktopOverlayStore.putClockText(text) }
        if (DesktopOverlaySettings.clockEnabled) source.start()
    }

    override fun stop() {
        if (!started) return
        started = false
        source.stop()
        runCatching { subscription?.close() }
        subscription = null
        DesktopOverlayStore.clockText.value = ""
    }

    override fun syncSwitch(enabled: Boolean) {
        if (enabled) source.start() else source.stop()
    }

    /** 设置变更后同步显示格式与刷新节奏。 */
    fun syncConfig() {
        source.updateConfig(currentConfig())
    }

    override fun close() {
        stop()
        OverlayFeatureRegistry.unregister(id)
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
