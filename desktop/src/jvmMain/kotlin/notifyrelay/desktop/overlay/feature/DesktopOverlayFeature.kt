package notifyrelay.desktop.overlay.feature

import notifyrelay.overlay.api.OverlayFeature
import notifyrelay.overlay.api.OverlayFeatureRegistry
import java.io.Closeable

/**
 * 可订阅数据源的叠加层扩展点骨架。
 *
 * [start] 与 [stop] 幂等；订阅在 [stop] 时释放。子类在自身 init 中调用 [register]，
 * 使注册发生在构造参数就绪之后。
 *
 * @param featureId 注册 id。
 */
abstract class DesktopOverlayFeature(
    private val featureId: String,
) : OverlayFeature,
    Closeable {
    override val id: String get() = featureId

    override val available: Boolean get() = true

    private var subscription: AutoCloseable? = null

    @Volatile
    private var started = false

    /** 订阅数据源，返回取消订阅句柄。 */
    protected abstract fun subscribe(): AutoCloseable

    /** 启动数据源。 */
    protected abstract fun startSource()

    /** 停止数据源。 */
    protected abstract fun stopSource()

    /** 清空该能力写入的叠加层状态。 */
    protected abstract fun clearSink()

    /** 订阅前的准备，例如推送最新配置。 */
    protected open fun beforeSubscribe() {}

    /** 注册到 [OverlayFeatureRegistry]。 */
    protected fun register() {
        OverlayFeatureRegistry.register(this)
    }

    override fun start() {
        if (started) return
        started = true
        beforeSubscribe()
        subscription = subscribe()
        startSource()
    }

    override fun stop() {
        if (!started) return
        started = false
        stopSource()
        runCatching { subscription?.close() }
        subscription = null
        clearSink()
    }

    override fun syncSwitch(enabled: Boolean) {
        if (enabled) startSource() else stopSource()
    }

    override fun close() {
        stop()
        OverlayFeatureRegistry.unregister(id)
    }
}
