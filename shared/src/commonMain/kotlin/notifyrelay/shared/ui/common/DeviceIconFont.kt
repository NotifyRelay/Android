package notifyrelay.shared.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import notifyrelay.shared.generated.resources.Res
import notifyrelay.shared.generated.resources.segsmdl2
import org.jetbrains.compose.resources.Font

/**
 * 设备列表使用的图标字体族（Segoe MDL2 Assets）。
 *
 * `BatteryIconConverter` 输出该字体的私有区字符，必须用同一字体渲染；
 * 字体位于 `:shared` 的 composeResources，Android 与 JVM 共用同一份。
 */
@Composable
fun deviceIconFontFamily(): FontFamily = FontFamily(Font(Res.font.segsmdl2))
