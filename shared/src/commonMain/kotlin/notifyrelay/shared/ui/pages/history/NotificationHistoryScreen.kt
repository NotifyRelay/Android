package notifyrelay.shared.ui.pages.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import notifyrelay.base.log.Logger
import notifyrelay.domain.feature.notification.history.NotificationHistoryPortRegistry
import notifyrelay.shared.ui.pages.rememberDeleteSwipeWidth
import notifyrelay.shared.ui.screen.GlobalSelectedDeviceHolder
import notifyrelay.shared.ui.viewmodel.NotificationHistoryViewModel
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 通知历史页。
 *
 * 平台交互（打开应用、提示、重发通知）经 [LocalNotificationActionPort] 注入；
 * 数据经 [NotificationHistoryPortRegistry] 注入的端口读取。
 */
@Composable
fun NotificationHistoryScreen() {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val actionPort = LocalNotificationActionPort.current ?: error("LocalNotificationActionPort 未提供")

    val viewModel: NotificationHistoryViewModel = notificationHistoryViewModel()

    val selectedDeviceObj by GlobalSelectedDeviceHolder.current()
    val selectedDevice = selectedDeviceObj?.uuid ?: "本机"

    LaunchedEffect(selectedDevice) {
        viewModel.loadNotifications(selectedDevice)
    }

    val uiState by viewModel.uiState.collectAsState()
    val installedPackages by viewModel.installedPackagesState.collectAsState()
    val appIconCache by viewModel.appIconCache.collectAsState()
    val pagingItems = viewModel.groupedPagingFlow.collectAsLazyPagingItems()

    val groupPackages =
        pagingItems.itemSnapshotList.items
            .map { it.packageName }
            .distinct()
    LaunchedEffect(groupPackages) {
        viewModel.preloadAppIcons(groupPackages)
    }

    val getCachedAppInfo: (String?) -> Pair<String, androidx.compose.ui.graphics.ImageBitmap?> = { packageName ->
        if (packageName.isNullOrBlank()) {
            "" to null
        } else {
            appIconCache[packageName] ?: (packageName to null)
        }
    }

    val clearHistory: () -> Unit = {
        try {
            viewModel.clearHistory()
        } catch (e: Exception) {
            Logger.e("NotifyRelay", "清除历史异常", e)
            actionPort.showToast("清除失败: " + (e.message ?: ""))
        }
    }
    val (deleteWidthPx, deleteWidth) = rememberDeleteSwipeWidth()

    NotificationHistoryScaffold(
        pagingItems = pagingItems,
        uiState = uiState,
        installedPackages = installedPackages,
        getCachedAppInfo = getCachedAppInfo,
        actionPort = actionPort,
        colorScheme = colorScheme,
        textStyles = textStyles,
        deleteWidthPx = deleteWidthPx,
        deleteWidth = deleteWidth,
        onClearHistory = clearHistory,
        onToggleGroup = { packageName -> viewModel.toggleGroupExpansion(packageName) },
        onDeleteGroup = { packageName -> viewModel.deleteGroup(packageName) },
        onDeleteNotification = { key -> viewModel.deleteNotification(key) },
    )
}

/** 以已注册的 [NotificationHistoryPortRegistry] 构造 [NotificationHistoryViewModel]。 */
@Composable
private fun notificationHistoryViewModel(): NotificationHistoryViewModel {
    val port = NotificationHistoryPortRegistry.current ?: error("NotificationHistoryPort 未注册")
    return viewModel(
        factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: kotlin.reflect.KClass<T>,
                    extras: CreationExtras,
                ): T = NotificationHistoryViewModel(port) as T
            },
    )
}
