package notifyrelay.desktop.overlay.feature

import notifyrelay.base.log.Logger
import notifyrelay.overlay.api.BalanceSnapshot
import notifyrelay.overlay.api.KeyMapping
import notifyrelay.overlay.api.KeyboardStateSnapshot
import notifyrelay.overlay.api.LogiBatteryDevice
import notifyrelay.overlay.api.OverlayFeature
import java.io.Closeable

/**
 * 叠加层扩展点装配。
 *
 * 持有四项 [OverlayFeature] 实现并统一负责启停；具体生命周期与数据订阅由各 Feature 自管，
 * 本类只做编排与供设置页访问的委托。新增能力只需实现 [OverlayFeature] 并在此注册，
 * 不再修改启停与订阅逻辑。
 *
 * 装配口径（用户裁定）：
 * - 余额默认关闭，用户填入凭据并开启后启动轮询；
 * - 罗技电池、键盘、时间默认开启；罗技仅在枚举到设备时显示；
 * - 键盘项开关与叠加层总开关同时满足才安装全局键盘钩子。
 */
class DesktopOverlayFeatures : Closeable {
    private val balance = DesktopBalanceFeature()
    private val logi = DesktopLogiBatteryFeature()
    private val keyboard = DesktopKeyboardFeature()
    private val clock = DesktopClockFeature()

    private val features: List<OverlayFeature> = listOf(balance, logi, keyboard, clock)

    @Volatile
    private var started = false

    init {
        current = this
    }

    /** 启动：各 Feature 自行订阅数据源并按开关启停。 */
    fun start() {
        if (started) return
        started = true
        features.forEach { feature ->
            if (feature.available) {
                runCatching { feature.start() }
                    .onFailure { Logger.w(TAG, "扩展点 ${feature.id} 启动失败", it) }
            }
        }
        Logger.i(TAG, "叠加层扩展点已启动")
    }

    /** 停止：各 Feature 释放资源与订阅。 */
    fun stop() {
        if (!started) return
        started = false
        features.forEach { feature ->
            runCatching { feature.stop() }
                .onFailure { Logger.w(TAG, "扩展点 ${feature.id} 停止失败", it) }
        }
        Logger.i(TAG, "叠加层扩展点已停止")
    }

    /** 开关变化后重新同步各数据源的启停状态。 */
    fun syncSwitches() {
        if (!started) return
        balance.syncSwitch(notifyrelay.desktop.platform.DesktopOverlaySettings.balanceEnabled)
        logi.syncSwitch(notifyrelay.desktop.platform.DesktopOverlaySettings.logiEnabled)
        keyboard.syncSwitch(notifyrelay.desktop.platform.DesktopOverlaySettings.keyboardEnabled)
        clock.syncSwitch(notifyrelay.desktop.platform.DesktopOverlaySettings.clockEnabled)
    }

    /** 时间显示格式变更后同步刷新节奏。 */
    fun syncClockConfig() {
        clock.syncConfig()
    }

    /** 释放全部资源；等价于 [stop]。 */
    override fun close() {
        stop()
        features.forEach { runCatching { (it as? Closeable)?.close() } }
        if (current === this) current = null
    }

    // ======== 设置页委托 ========

    /** 当前余额快照（供设置页展示）。 */
    fun balanceSnapshot(): BalanceSnapshot = balance.source.snapshot

    /** 当前罗技设备快照（供设置页展示）。 */
    fun logiDevices(): List<LogiBatteryDevice> = logi.source.devices()

    /** 当前按键状态（供设置页展示）。 */
    fun keyboardState(): KeyboardStateSnapshot = keyboard.source.state()

    /** 当前映射规则。 */
    fun mappings(): List<KeyMapping> = keyboard.source.mappings()

    /** 订阅按键状态；供设置页录入组合键使用。 */
    fun observeKeyboard(listener: (KeyboardStateSnapshot) -> Unit): AutoCloseable = keyboard.source.observeState(listener)

    /** 覆写映射规则并即时生效。 */
    fun setMappings(mappings: List<KeyMapping>) {
        notifyrelay.desktop.platform.DesktopOverlaySettings.keyboardMappings = mappings
        keyboard.source.setMappings(mappings)
    }

    /** 立即查询一次余额；失败返回 null。 */
    suspend fun fetchBalanceOnce(): Double? = balance.source.fetchOnce()

    /** 清空余额历史。 */
    fun clearBalanceHistory() = balance.source.clearHistory()

    /** 是否已配置余额凭据。 */
    fun isBalanceConfigured(): Boolean = balance.source.isConfigured

    /** 罗技原生库是否可用。 */
    fun isLogiAvailable(): Boolean = logi.source.available

    /** 立即枚举一次罗技设备，不改变轮询状态。 */
    fun refreshLogiNow() = logi.source.refreshNow()

    /** 设置或清除罗技设备自定义名；空名表示回退原生名。 */
    fun setLogiDeviceName(
        deviceId: String,
        name: String?,
    ) = logi.source.setDeviceNameOverride(deviceId, name)

    /** 订阅罗技设备快照；供设置页展示设备列表。 */
    fun observeLogiDevices(listener: (List<LogiBatteryDevice>) -> Unit): AutoCloseable = logi.source.observe(listener)

    /** 键盘监控是否可用。 */
    fun isKeyboardAvailable(): Boolean = keyboard.source.available

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
    }
}
