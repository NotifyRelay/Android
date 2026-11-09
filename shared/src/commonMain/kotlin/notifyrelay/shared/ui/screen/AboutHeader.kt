package notifyrelay.shared.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 关于页头部：应用图标与应用名。
 *
 * 图标按圆角矩形显示；取不到图标时上边距改为 24dp，使应用名不会贴顶。
 *
 * 声明为 [ColumnScope] 扩展：两处调用都在竖直 `Column` 内，头部需要水平居中。
 *
 * @param appIcon 应用图标；为 null 时不渲染图标。
 */
@Composable
fun ColumnScope.AboutHeader(appIcon: ImageBitmap?) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

    appIcon?.let {
        Image(
            bitmap = it,
            contentDescription = "应用图标",
            modifier =
                Modifier
                    .padding(top = 24.dp)
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .align(Alignment.CenterHorizontally),
        )
    }

    Text(
        text = "Notify Relay",
        style = textStyles.title1,
        color = colorScheme.primary,
        modifier =
            Modifier
                .padding(top = if (appIcon != null) 16.dp else 24.dp, bottom = 16.dp)
                .align(Alignment.CenterHorizontally),
    )
}
