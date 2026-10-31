package notifyrelay.overlay.api

/**
 * 时间浮窗配置。
 *
 * 时间浮窗无外部数据源：时间由实现侧读取系统时钟（JVM `java.time`），本配置只描述显示格式。
 */
data class ClockConfig(
    /** 是否显示秒。 */
    val showSeconds: Boolean = true,
    /** 是否 24 小时制。 */
    val use24Hour: Boolean = true,
)

/**
 * 时间数据源。
 *
 * 实现侧完全在 JVM 内完成（`java.time.LocalTime`），不使用 JNA；
 * 按配置决定刷新节奏（秒级或分钟级），变化时通知订阅方。
 */
interface ClockSource {
    /** 当前格式化时间文案。 */
    fun text(): String

    /** 启动按配置节奏刷新；已启动时幂等。 */
    fun start()

    /** 停止刷新；未启动时幂等。 */
    fun stop()

    /** 更新显示格式并重置刷新节奏。 */
    fun updateConfig(config: ClockConfig)

    /** 时间文案变化订阅；返回取消订阅函数。 */
    fun observe(listener: (String) -> Unit): AutoCloseable
}
