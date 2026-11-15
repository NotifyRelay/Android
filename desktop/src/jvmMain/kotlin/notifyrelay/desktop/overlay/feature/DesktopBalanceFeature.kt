package notifyrelay.desktop.overlay.feature

import notifyrelay.desktop.overlay.DesktopOverlayStore
import notifyrelay.desktop.platform.DesktopOverlaySettings
import notifyrelay.overlay.api.BalanceSnapshot

/**
 * 余额扩展点：封装 [DesktopBalanceSource] 的生命周期与数据订阅。
 *
 * 启停骨架见 [DesktopOverlayFeature]。
 */
class DesktopBalanceFeature(
    val source: DesktopBalanceSource = DesktopBalanceSource(),
) : DesktopOverlayFeature(ID) {
    init {
        register()
    }

    override fun subscribe(): AutoCloseable = source.observe { snapshot -> DesktopOverlayStore.putBalance(snapshot) }

    override fun startSource() {
        if (DesktopOverlaySettings.balanceEnabled) source.start()
    }

    override fun stopSource() = source.stop()

    override fun clearSink() {
        DesktopOverlayStore.balance.value = BalanceSnapshot()
    }

    companion object {
        const val ID = "balance"
    }
}
