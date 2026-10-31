package notifyrelay.overlay.api

/**
 * 叠加层契约：由桌面端实现并消费，界面与数据源之间不互相引用具体类型。
 *
 * 分层分工：
 * - 本模块只放数据模型与能力接口，无实现、无平台依赖；
 * - 实现放 `:desktop` 的 `overlay.feature` 与 `platform` 包；
 * - 渲染只读本模块的数据模型。
 *
 * 能力清单：余额（BalanceSource）、罗技电池（LogiBatterySource）、
 * 键盘（KeyboardSource）；时间浮窗只在本文件。
 *
 * 时间浮窗配置无数据源：时间由渲染侧读取系统时间，本配置只描述显示格式。
 */
data class ClockConfig(
    /** 是否显示秒。 */
    val showSeconds: Boolean = true,
    /** 是否 24 小时制。 */
    val use24Hour: Boolean = true,
)
