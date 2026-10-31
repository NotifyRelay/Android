package notifyrelay.desktop.overlay.feature

import notifyrelay.desktop.overlay.DesktopOverlayStore
import notifyrelay.desktop.platform.DesktopOverlaySettings
import notifyrelay.overlay.api.BalanceSnapshot
import notifyrelay.overlay.api.OverlayFeature
import notifyrelay.overlay.api.OverlayFeatureRegistry
import java.io.Closeable

/**
 * 余额扩展点：封装 [DesktopBalanceSource] 的生命周期与数据订阅。
 *
 * 实现 [OverlayFeature] 并注册到 [OverlayFeatureRegistry]；
 * 编排方只经注册表统一启停，不再逐项硬编码。
 */
class DesktopBalanceFeature(
    val source: DesktopBalanceSource = DesktopBalanceSource(),
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
        subscription = source.observe { snapshot -> DesktopOverlayStore.putBalance(snapshot) }
        if (DesktopOverlaySettings.balanceEnabled) source.start()
    }

    override fun stop() {
        if (!started) return
        started = false
        source.stop()
        runCatching { subscription?.close() }
        subscription = null
        DesktopOverlayStore.balance.value = BalanceSnapshot()
    }

    override fun syncSwitch(enabled: Boolean) {
        if (enabled) source.start() else source.stop()
    }

    override fun close() {
        stop()
        OverlayFeatureRegistry.unregister(id)
    }

    companion object {
        const val ID = "balance"
    }
}
