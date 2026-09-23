package com.xzyht.notifyrelay.ui.pages

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.xzyht.notifyrelay.ui.pages.history.NotificationHistoryScaffold
import com.xzyht.notifyrelay.ui.screen.GlobalSelectedDeviceHolder
import com.xzyht.notifyrelay.ui.viewmodel.NotificationHistoryViewModel
import notifyrelay.base.util.Logger
import notifyrelay.base.util.ToastUtils
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class DragValue { Center, End }

// 防抖 Toast（文件级顶层对象）
internal object ToastDebounce {
    var lastToastTime: Long = 0L
    const val DEBOUNCE_MILLIS: Long = 1500L
}

@Composable
fun NotificationHistoryScreen() {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val context = LocalContext.current
    val viewModel: NotificationHistoryViewModel =
        viewModel(
            factory = NotificationHistoryViewModel.Factory(context.applicationContext as Application),
        )

    // 创建协程作用域用于删除操作等
    // （删除操作已随 NotificationListBlock 自带作用域，此处不再需要）

    val selectedDeviceObj by GlobalSelectedDeviceHolder.current()
    val selectedDevice = selectedDeviceObj?.uuid ?: "本机"

    LaunchedEffect(selectedDevice) {
        viewModel.loadNotifications(selectedDevice)
    }

    val isDarkTheme = isSystemInDarkTheme()
    LaunchedEffect(isDarkTheme) {
        val window = (context as? Activity)?.window
        window?.let {
            val decorView = it.decorView
            WindowInsetsControllerCompat(it, decorView).isAppearanceLightStatusBars = !isDarkTheme
        }
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

    val getCachedAppInfo: (String?) -> Pair<String, Bitmap?> = { packageName ->
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
            ToastUtils.showShortToast(
                context,
                "清除失败: ${e.message}",
            )
        }
    }
    val (deleteWidthPx, deleteWidth) = rememberDeleteSwipeWidth()

    NotificationHistoryScaffold(
        pagingItems = pagingItems,
        uiState = uiState,
        installedPackages = installedPackages,
        getCachedAppInfo = getCachedAppInfo,
        context = context,
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
