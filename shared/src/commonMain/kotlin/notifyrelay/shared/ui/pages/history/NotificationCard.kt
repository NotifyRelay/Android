package notifyrelay.shared.ui.pages.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import notifyrelay.base.util.TimestampFormatter
import notifyrelay.domain.feature.notification.history.NotificationHistoryEntry
import notifyrelay.shared.ui.pages.history.NotificationActionPort
import notifyrelay.shared.ui.viewmodel.RemoteFilterViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 提示去抖：避免连续点击重复弹出同一条提示。 */
internal object ToastDebounce {
    var lastToastTime: Long = 0L
    const val DEBOUNCE_MILLIS: Long = 1500L
}

@Composable
fun NotificationCard(
    record: NotificationHistoryEntry,
    appIcon: androidx.compose.ui.graphics.ImageBitmap?,
    actionPort: NotificationActionPort,
    getCachedAppInfo: (String?) -> Pair<String, androidx.compose.ui.graphics.ImageBitmap?>,
    cardColor: Color,
    contentColor: Color,
    installedPackages: Set<String>,
    /**
     * 可选长按回调（默认 `null`，即不注册长按，历史页行为与改动前逐字一致）。
     *
     * 通知字段转储页用它触发「复制该通知的完整字段」或「下载该通知的 JSON」；
     * [enableClick] 为 `false` 时点击不执行「启动应用」，仅保留长按。
     */
    onLongClick: (() -> Unit)? = null,
    /** 是否启用点击启动对应应用；转储页传 `false` 以免误触跳转。 */
    enableClick: Boolean = true,
    /** 是否在卡片上额外外显原始包名（默认 `false`，历史页不显示，行为不变）。 */
    showPackageName: Boolean = false,
    viewModel: RemoteFilterViewModel? = null,
) {
    val filterViewModel = viewModel ?: remoteFilterViewModel()
    val notificationTextStyles = MiuixTheme.textStyles
    val cardColorScheme = MiuixTheme.colorScheme

    // 对包名进行等价映射，使用缓存的包名集合，避免同步加载
    val mappedPkg = filterViewModel.mapToLocalPackage(record.packageName, installedPackages)

    // 使用映射后的包名获取应用信息
    val appInfo: Pair<String, androidx.compose.ui.graphics.ImageBitmap?> = getCachedAppInfo(mappedPkg)
    val (_, mappedAppIcon) = appInfo
    val displayAppIcon = mappedAppIcon ?: appIcon

    // 修正：单条通知卡片标题应为原始通知标题
    val displayTitle = record.title ?: "(无标题)"

    // 点击启动对应应用；[enableClick] 为 false（转储页）时不注册点击，避免误触跳转。
    // 逻辑与改动前逐字一致，仅改为按需挂载。
    val handleClick: (() -> Unit)? =
        if (!enableClick) {
            null
        } else {
            {
                // 跳转到对应应用主界面
                val pkg = record.packageName
                if (pkg.isNotEmpty()) {
                    // 应用等价映射，使用缓存的包名集合，避免同步加载
                    val mappedPkg = filterViewModel.mapToLocalPackage(pkg, installedPackages)
                    val failure = actionPort.openApp(mappedPkg)
                    if (failure != null) {
                        val now = System.currentTimeMillis()
                        if (now - ToastDebounce.lastToastTime > ToastDebounce.DEBOUNCE_MILLIS) {
                            actionPort.showToast(failure)
                            ToastDebounce.lastToastTime = now
                        }
                    } else {
                        // 仅在即将跳转前显示通知标题和内容
                        val title = record.title ?: "(无标题)"
                        val text = record.text ?: "(无内容)"
                        actionPort.resendAsHighPriority(title, text)
                    }
                }
            }
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        onClick = handleClick,
        onLongPress = onLongClick,
        cornerRadius = 8.dp,
        insideMargin = PaddingValues(12.dp),
        colors =
            CardDefaults.defaultColors(
                color = cardColor,
                contentColor = contentColor,
            ),
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (displayAppIcon != null) {
                Image(
                    bitmap = displayAppIcon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            // 标题显示为原始通知标题
            Text(
                text = displayTitle,
                style = notificationTextStyles.body2.copy(color = cardColorScheme.onSurface),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (showPackageName) {
            // 转储页需要直接看到原始包名（未经等价映射），便于与通知结构比对
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = record.packageName,
                style = notificationTextStyles.body2.copy(color = cardColorScheme.primary),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = record.text ?: "(无内容)",
            style = notificationTextStyles.body1.copy(color = cardColorScheme.onSurface),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text =
                TimestampFormatter.format(record.time),
            style = notificationTextStyles.body2.copy(color = cardColorScheme.onSurfaceSecondary),
        )
    }
}

/** 以已注册端口构造 [RemoteFilterViewModel]（供卡片独立使用时）。 */
@androidx.compose.runtime.Composable
private fun remoteFilterViewModel(): RemoteFilterViewModel {
    val filterPort =
        notifyrelay.domain.feature.notification.filter.RemoteFilterPortRegistry.current
            ?: error("RemoteFilterPort 未注册")
    val appsPort =
        notifyrelay.shared.feature.appslist.LocalAppsPortRegistry.current
            ?: error("LocalAppsPort 未注册")
    val iconPort =
        notifyrelay.shared.feature.appslist.AppIconPortRegistry.current
            ?: error("AppIconPort 未注册")
    return androidx.lifecycle.viewmodel.compose.viewModel(
        factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(
                    modelClass: kotlin.reflect.KClass<T>,
                    extras: androidx.lifecycle.viewmodel.CreationExtras,
                ): T = RemoteFilterViewModel(filterPort, appsPort, iconPort) as T
            },
    )
}
