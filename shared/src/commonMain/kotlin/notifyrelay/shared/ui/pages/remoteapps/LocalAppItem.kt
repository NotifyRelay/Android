package notifyrelay.shared.ui.pages.remoteapps

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import notifyrelay.domain.feature.appslist.model.LocalAppInfo

/** 本机应用网格单元。 */
@Composable
fun LocalAppItem(
    app: LocalAppInfo,
    iconBitmap: ImageBitmap?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    AppGridCell(
        appName = app.appName,
        iconBitmap = iconBitmap,
        cellWidth = 80.dp,
        iconSize = 64.dp,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}
