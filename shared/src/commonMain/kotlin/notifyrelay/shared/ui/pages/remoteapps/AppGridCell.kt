package notifyrelay.shared.ui.pages.remoteapps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 应用网格单元：图标 + 名称，图标缺失时回落到齿轮占位。
 *
 * 本机与远端列表的单元只有尺寸与角标不同，因此尺寸由调用方给出，
 * 覆盖层（加载中）与角标（已置顶）经槽位注入。
 *
 * @param appName 应用显示名。
 * @param iconBitmap 应用图标；为 null 时显示占位。
 * @param cellWidth 单元格宽度。
 * @param iconSize 图标区域边长；占位图标按四分之一绘制。
 * @param onClick 单击。
 * @param onLongClick 长按。
 * @param overlay 覆盖在图标之上的内容（如加载指示）。
 * @param badge 图标右上角的角标（如置顶标记）。
 */
@Composable
fun AppGridCell(
    appName: String,
    iconBitmap: ImageBitmap?,
    cellWidth: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    badge: (@Composable BoxScope.() -> Unit)? = null,
) {
    val colorScheme = MiuixTheme.colorScheme

    Column(
        modifier =
            Modifier
                .width(cellWidth)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(iconSize)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = appName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        imageVector = MiuixIcons.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize / 2),
                        tint = colorScheme.onSurfaceSecondary,
                    )
                }
            }
            overlay?.invoke(this)
            badge?.invoke(this)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = appName,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            color = colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
