package notifyrelay.desktop.overlay.feature

import notifyrelay.base.log.Logger
import notifyrelay.desktop.overlay.DesktopOverlayStore
import notifyrelay.desktop.platform.DesktopOverlaySettings
import notifyrelay.overlay.api.BalanceSnapshot
import notifyrelay.overlay.api.BalanceSource
import notifyrelay.overlay.api.KeyMapping
import notifyrelay.overlay.api.KeyboardSource
import notifyrelay.overlay.api.KeyboardStateSnapshot
import notifyrelay.overlay.api.LogiBatteryDevice
import notifyrelay.overlay.api.LogiBatterySource
import java.io.Closeable

/**
 * 叠加层扩展点装配。
 *
 * 汇总四项能力的实现与设置，统一负责「按开关启动 / 停止」与「把数据推入叠加层状态」；
 * 不引入宿主编排器，由 [notifyrelay.desktop.overlay.DesktopOverlayWindow] 直接调用。
 *
 * 装配口径（用户裁定）：
 * - 余额默认关闭，用户填入凭据并开启后启动轮询；
 * - 罗技电池、键盘、时间默认开启；罗技仅在枚举到设备时显示；
 * - 键盘项开关与叠加层总开关同时满足才安装全局键盘钩子。
 */
class DesktopOverlayFeatures(
    private val balanceSource: BalanceSource = DesktopBalanceSource(),
    private val logiSource: LogiBatterySource = DesktopLogiBatterySource(),
    private val keyboardSource: KeyboardSource =
        DesktopKeyboardSource(
            mappingsProvider = { DesktopOverlaySettings.keyboardMappings },
            // 构造参数在实例初始化前求值，故引用伴生函数而非成员函数
            enabledProvider = { keyboardActive() },
        ),
) : Closeable {
    companion object {
        const val TAG = "OverlayFeatures"

        /**
         * 当前活动实例。
         *
         * 设置页与叠加层窗口处于不同 Compose 树，故经此引用触发开关同步；
         * 未启用叠加层时为空，设置页按无实例处理（改动在下次启动生效）。
         */
        @Volatile
        var current: DesktopOverlayFeatures? = null
            private set

        /** 键盘项是否处于活动状态：叠加层总开关与键盘项开关同时开启。 */
        fun keyboardActive(): Boolean = DesktopOverlaySettings.enabled && DesktopOverlaySettings.keyboardEnabled
    }

    private val subscriptions = mutableListOf<AutoCloseable>()

    @Volatile
    private var started = false

    init {
        current = this
    }

    /** 启动：订阅数据源并推入叠加层状态，再按各自开关启停后台任务。 */
    fun start() {
        if (started) return
        started = true

        subscribe()

        // 余额：仅在有凭据且开关开启时轮询
        if (DesktopOverlaySettings.balanceEnabled) {
            balanceSource.start()
        } else {
            Logger.d(TAG, "余额浮窗未开启，不启动轮询")
        }

        // 罗技：开关开启即轮询；无设备时不显示由渲染侧按空集合处理
        if (DesktopOverlaySettings.logiEnabled) {
            logiSource.start()
        }

        // 键盘：安装全局低级键盘钩子
        keyboardSource.syncState()

        Logger.i(TAG, "叠加层扩展点已启动")
    }

    /** 停止：卸载钩子、停止轮询并解除订阅。 */
    fun stop() {
        if (!started) return
        started = false

        keyboardSource.uninstall()
        logiSource.stop()
        balanceSource.stop()

        subscriptions.forEach { runCatching { it.close() } }
        subscriptions.clear()

        DesktopOverlayStore.clearFeatures()
        Logger.i(TAG, "叠加层扩展点已停止")
    }

    /** 开关变化后重新同步各数据源的启停状态。 */
    fun syncSwitches() {
        if (!started) return

        if (DesktopOverlaySettings.balanceEnabled) balanceSource.start() else balanceSource.stop()
        if (DesktopOverlaySettings.logiEnabled) logiSource.start() else logiSource.stop()
        keyboardSource.syncState()
    }

    /** 释放全部资源；等价于 [stop]。 */
    override fun close() {
        stop()
        if (current === this) current = null
    }

    /** 订阅四个数据源并把快照写入叠加层状态。 */
    private fun subscribe() {
        subscriptions.add(
            balanceSource.observe { snapshot -> DesktopOverlayStore.putBalance(snapshot) },
        )
        subscriptions.add(
            logiSource.observe { devices -> DesktopOverlayStore.putLogiDevices(devices) },
        )
        subscriptions.add(
            keyboardSource.observeState { snapshot -> DesktopOverlayStore.putKeyboardState(snapshot) },
        )
        subscriptions.add(
            keyboardSource.observeMappingHint { text -> DesktopOverlayStore.putKeyboardHint(text) },
        )
    }

    /** 当前余额快照（供设置页展示）。 */
    fun balanceSnapshot(): BalanceSnapshot = balanceSource.snapshot

    /** 当前罗技设备快照（供设置页展示）。 */
    fun logiDevices(): List<LogiBatteryDevice> = logiSource.devices()

    /** 当前按键状态（供设置页展示）。 */
    fun keyboardState(): KeyboardStateSnapshot = keyboardSource.state()

    /** 当前映射规则。 */
    fun mappings(): List<KeyMapping> = keyboardSource.mappings()

    /** 订阅按键状态；供设置页录入组合键使用。 */
    fun observeKeyboard(listener: (KeyboardStateSnapshot) -> Unit): AutoCloseable = keyboardSource.observeState(listener)

    /** 覆写映射规则并即时生效。 */
    fun setMappings(mappings: List<KeyMapping>) {
        DesktopOverlaySettings.keyboardMappings = mappings
        keyboardSource.setMappings(mappings)
    }

    /** 立即查询一次余额；失败返回 null。 */
    suspend fun fetchBalanceOnce(): Double? = balanceSource.fetchOnce()

    /** 清空余额历史。 */
    fun clearBalanceHistory() = balanceSource.clearHistory()

    /** 是否已配置余额凭据。 */
    fun isBalanceConfigured(): Boolean = balanceSource.isConfigured

    /** 罗技原生库是否可用。 */
    fun isLogiAvailable(): Boolean = logiSource.available

    /** 键盘监控是否可用。 */
    fun isKeyboardAvailable(): Boolean = keyboardSource.available
}
