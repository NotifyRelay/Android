package notifyrelay.overlay.api

/**
 * 叠加层元素的锚点与外观配置。
 *
 * 坐标按屏幕宽高的百分比（0~100）表达，与 Windows 叠加层同口径；
 * JVM 叠加层为覆盖主屏的单一窗口，故无目标屏字段。
 */
data class OverlayAnchor(
    /** 是否显示该元素。 */
    val enabled: Boolean = true,
    /** 水平位置，占屏宽百分比（0~100）。 */
    val xPercent: Float = 50f,
    /** 垂直位置，占屏高百分比（0~100）。 */
    val yPercent: Float = 10f,
    /** 缩放倍数。 */
    val scale: Float = 1f,
    /** 文本颜色，`#RRGGBB`。 */
    val colorHex: String = "#FFFFFF",
    /** 文本描边宽度（像素）。 */
    val outlineWidth: Float = 2f,
)

/** DeepSeek 余额历史条目。 */
data class BalanceHistoryItem(
    /** 记录时刻（毫秒）。 */
    val time: Long,
    /** 该时刻的余额。 */
    val balance: Double,
    /** 相对上一条的变化量。 */
    val change: Double = 0.0,
    /** 变化类型：`init` / `+` / `-` / `=`。 */
    val changeType: String = "init",
    /** 合并条数（历史裁剪时同向连续项被合并）。 */
    val mergeCount: Int = 1,
)

/** DeepSeek 余额快照。 */
data class BalanceSnapshot(
    /** 当前余额；无数据时为 null，界面显示占位。 */
    val balance: Double? = null,
    /** 相对上一条记录的变化量。 */
    val change: Double = 0.0,
    /** 是否正在轮询。 */
    val isPolling: Boolean = false,
)

/**
 * DeepSeek 余额数据源。
 *
 * 实现侧自行负责轮询节奏与历史持久化，本接口只表达「启动 / 停止 / 查询一次 / 清空历史」。
 */
interface BalanceSource {
    /** 当前快照。 */
    val snapshot: BalanceSnapshot

    /** 历史记录（时间升序）。 */
    fun history(): List<BalanceHistoryItem>

    /** 是否已配置凭据；未配置时 [start] 不启动轮询。 */
    val isConfigured: Boolean

    /** 启动轮询；已启动时幂等。 */
    fun start()

    /** 停止轮询；未启动时幂等。 */
    fun stop()

    /** 立即查询一次余额；失败返回 null。 */
    suspend fun fetchOnce(): Double?

    /** 清空历史并落盘。 */
    fun clearHistory()

    /** 快照变化订阅；返回取消订阅函数。 */
    fun observe(listener: (BalanceSnapshot) -> Unit): AutoCloseable
}
