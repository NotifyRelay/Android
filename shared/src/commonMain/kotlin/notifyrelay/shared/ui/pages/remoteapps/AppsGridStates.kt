package notifyrelay.shared.ui.pages.remoteapps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 应用网格的三类空态共用的垂直排布：图标、间距、说明文字或操作。 */
@Composable
private fun CenteredNotice(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

/**
 * 应用网格的加载失败态：图标 + 错误文本，可选重试。
 *
 * @param message 失败说明，直接取自状态的 error 字段。
 * @param onRetry 重试动作；为 null 时不显示按钮。
 */
@Composable
fun AppsErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    val colorScheme = MiuixTheme.colorScheme
    CenteredNotice {
        Icon(
            imageVector = MiuixIcons.Delete,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, color = colorScheme.error)
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(8.dp))
            top.yukonga.miuix.kmp.basic
                .Button(onClick = onRetry) { Text("重试") }
        }
    }
}

/**
 * 应用网格的加载中态：进度圈，可选说明文字。
 *
 * @param label 说明文字；为 null 时只显示进度圈。
 */
@Composable
fun AppsLoadingState(label: String? = null) {
    val colorScheme = MiuixTheme.colorScheme
    CenteredNotice {
        CircularProgressIndicator()
        if (label != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = label, color = colorScheme.onSurfaceSecondary)
        }
    }
}

/**
 * 应用网格的空态：图标 + 说明。
 *
 * @param message 说明文字。
 * @param icon 空态图标。
 */
@Composable
fun AppsEmptyState(
    message: String,
    icon: ImageVector,
) {
    val colorScheme = MiuixTheme.colorScheme
    CenteredNotice {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = colorScheme.onSurfaceSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, color = colorScheme.onSurfaceSecondary)
    }
}

/** 应用网格的列布局参数：本机与远端两个列表共用。 */
val AppsGridContentPadding = PaddingValues(4.dp)

/** 应用网格的横向间距。 */
val AppsGridHorizontalSpacing = 8.dp

/** 应用网格的纵向间距。 */
val AppsGridVerticalSpacing = 12.dp
