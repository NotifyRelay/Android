package notifyrelay.desktop.overlay.feature

import notifyrelay.desktop.overlay.DesktopOverlayStore
import notifyrelay.overlay.api.OverlayFeature
import notifyrelay.overlay.api.OverlayFeatureRegistry
import java.io.Closeable

/**
 * 键盘监控扩展点：封装 [DesktopKeyboardSource] 的生命周期与数据订阅。
 *
 * 实现 [OverlayFeature] 并注册到 [notifyrelay.overlay.api.OverlayFeatureRegistry]。
 */
class DesktopKeyboardFeature(
    val source: DesktopKeyboardSource =
        DesktopKeyboardSource(
            mappingsProvider = { notifyrelay.desktop.platform.DesktopOverlaySettings.keyboardMappings },
            enabledProvider = { keyboardActive() },
        ),
) : OverlayFeature,
    Closeable {
    override val id = ID
    override val available = source.available

    private var stateSubscription: AutoCloseable? = null
    private var hintSubscription: AutoCloseable? = null

    @Volatile
    private var started = false

    init {
        OverlayFeatureRegistry.register(this)
    }

    override fun start() {
        if (started) return
        started = true
        stateSubscription = source.observeState { snapshot -> DesktopOverlayStore.putKeyboardState(snapshot) }
        hintSubscription = source.observeMappingHint { text -> DesktopOverlayStore.putKeyboardHint(text) }
        source.syncState()
    }

    override fun stop() {
        if (!started) return
        started = false
        source.uninstall()
        runCatching { stateSubscription?.close() }
        runCatching { hintSubscription?.close() }
        stateSubscription = null
        hintSubscription = null
        DesktopOverlayStore.clearFeatures()
    }

    override fun syncSwitch(enabled: Boolean) {
        source.syncState()
    }

    override fun close() {
        stop()
        OverlayFeatureRegistry.unregister(id)
    }

    companion object {
        const val ID = "keyboard"

        /** 键盘项是否处于活动状态：叠加层总开关与键盘项开关同时开启。 */
        fun keyboardActive(): Boolean =
            notifyrelay.desktop.platform.DesktopOverlaySettings.enabled &&
                notifyrelay.desktop.platform.DesktopOverlaySettings.keyboardEnabled
    }
}
