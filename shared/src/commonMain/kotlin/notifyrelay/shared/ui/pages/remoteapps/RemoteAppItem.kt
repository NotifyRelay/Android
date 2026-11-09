package notifyrelay.shared.ui.pages.remoteapps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import notifyrelay.domain.feature.appslist.model.RemoteAppInfo
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 远端应用网格单元；加载中与已置顶各有额外视觉层。 */
@Composable
fun AppItem(
    app: RemoteAppInfo,
    iconBitmap: ImageBitmap?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme

    AppGridCell(
        appName = app.appName,
        iconBitmap = iconBitmap,
        cellWidth = 72.dp,
        iconSize = 56.dp,
        onClick = onClick,
        onLongClick = onLongClick,
        overlay = {
            if (app.isLoading) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        },
        badge = {
            if (app.isPinned) {
                Icon(
                    imageVector = MiuixIcons.Pin,
                    contentDescription = "已置顶",
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(16.dp)
                            .background(colorScheme.primary, CircleShape)
                            .padding(2.dp),
                    tint = colorScheme.onPrimary,
                )
            }
        },
    )
}
