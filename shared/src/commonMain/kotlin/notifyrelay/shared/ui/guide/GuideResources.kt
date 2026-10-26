package notifyrelay.shared.ui.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter

/** 引导页所需平台资源。 */
data class GuideResources(
    /** 读取 raw 资源的文本内容；不存在返回 null。 */
    val readRawText: (String) -> String?,
    /** 绘制指定名称的图标；不存在返回 null。 */
    val drawablePainter: @Composable (String) -> Painter?,
)

/** 由宿主在引导页根节点注入。 */
val LocalGuideResources = staticCompositionLocalOf<GuideResources?> { null }
