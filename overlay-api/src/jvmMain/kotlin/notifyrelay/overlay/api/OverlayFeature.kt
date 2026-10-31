package notifyrelay.overlay.api

import java.util.concurrent.ConcurrentHashMap

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

/**
 * 叠加层扩展点的统一生命周期契约。
 *
 * 各浮窗能力（余额 / 罗技电池 / 键盘 / 时间 / …）实现本接口并经
 * [OverlayFeatureRegistry] 注册；编排方（`DesktopOverlayFeatures`）只遍历注册表
 * 统一启停，新增能力无需修改编排方与本模块的核心结构。
 *
 * 分层分工：
 * - 本模块只放数据模型与能力接口，无实现、无平台依赖；
 * - 实现放 `:desktop` 的 `overlay.feature` 与 `platform` 包；
 * - 渲染只读本模块的数据模型。
 */
interface OverlayFeature {
    /** 唯一标识，用于注册表索引与设置页路由。 */
    val id: String

    /** 是否可使用（原生库可用 / 钩子可安装 等）；不可用时编排方跳过启动。 */
    val available: Boolean

    /** 启动数据源与后台任务；已启动时幂等。 */
    fun start()

    /** 停止并释放资源；未启动时幂等。 */
    fun stop()

    /** 按开关状态同步启停。 */
    fun syncSwitch(enabled: Boolean)
}

/**
 * 扩展点注册表。
 *
 * 实现侧在构造时自行注册，编排方经 [all] 遍历；新增能力只需实现 [OverlayFeature]
 * 并注册，不必修改编排方代码。
 */
object OverlayFeatureRegistry {
    private val features = ConcurrentHashMap<String, OverlayFeature>()

    /** 注册或覆盖同 id 的能力。 */
    fun register(feature: OverlayFeature) {
        features[feature.id] = feature
    }

    /** 注销。 */
    fun unregister(id: String) {
        features.remove(id)
    }

    /** 按 id 取能力。 */
    fun get(id: String): OverlayFeature? = features[id]

    /** 全部已注册能力（按注册顺序）。 */
    fun all(): List<OverlayFeature> = features.values.toList()

    /** 清空注册表。 */
    fun clear() {
        features.clear()
    }
}
