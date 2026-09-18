package com.xzyht.notifyrelay.ui.pages

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.xzyht.notifyrelay.ui.common.DoubleClickConfirmButton
import com.xzyht.notifyrelay.ui.pages.superisland.SuperIslandHistoryListBlock
import com.xzyht.notifyrelay.ui.viewmodel.SuperIslandHistoryViewModel
import notifyrelay.base.util.Logger
import notifyrelay.data.StorageManager
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UISuperIslandHistory() {
    val context = LocalContext.current
    var includeImageDataOnCopy by remember { mutableStateOf(StorageManager.getBoolean(context, "superisland_copy_image_data", false)) }

    val viewModel: SuperIslandHistoryViewModel =
        viewModel(
            factory = SuperIslandHistoryViewModel.Factory(context.applicationContext as android.app.Application),
        )

    val isDarkTheme = isSystemInDarkTheme()
    LaunchedEffect(isDarkTheme) {
        val window = (context as? Activity)?.window
        window?.let {
            val decorView = it.decorView
            WindowInsetsControllerCompat(it, decorView).isAppearanceLightStatusBars = !isDarkTheme
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val pagingItems = viewModel.groupedPagingFlow.collectAsLazyPagingItems()

    val groupPackages =
        pagingItems.itemSnapshotList.items
            .map { it.packageName }
            .distinct()
    LaunchedEffect(groupPackages) {
        viewModel.preloadAppIcons(groupPackages)
    }

    val clearHistory: () -> Unit = {
        try {
            viewModel.clearHistory()
        } catch (e: Exception) {
            Logger.e("NotifyRelay", "清除超级岛历史异常", e)
            Toast.makeText(context, "清除失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val density = LocalDensity.current
    val deleteWidthPx = with(density) { 80.dp.toPx() }
    val deleteWidth = 80.dp

    Scaffold(
        containerColor = colorScheme.background,
        floatingToolbar = {
            if (pagingItems.itemCount > 0) {
                FloatingToolbar(
                    color = colorScheme.primary,
                    cornerRadius = 20.dp,
                    showDivider = false,
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DoubleClickConfirmButton(
                            text = "清空超级岛历史",
                            confirmText = "确认?",
                            onClick = {},
                            onConfirm = clearHistory,
                            colors = ButtonDefaults.buttonColors(color = colorScheme.onSurface),
                            confirmColors = ButtonDefaults.buttonColors(color = colorScheme.error),
                        )
                    }
                }
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomEnd,
        content = { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(12.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "复制图片详细信息",
                        style = textStyles.body2,
                        color = colorScheme.onSurface,
                    )
                    Switch(
                        checked = includeImageDataOnCopy,
                        onCheckedChange = {
                            includeImageDataOnCopy = it
                            StorageManager.putBoolean(context, "superisland_copy_image_data", it)
                        },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (pagingItems.itemCount == 0) {
                    Text("暂无超级岛历史记录", style = textStyles.body2, color = colorScheme.onSurfaceVariantSummary)
                } else {
                    SuperIslandHistoryListBlock(
                        pagingItems = pagingItems,
                        expandedGroups = uiState.expandedGroups,
                        includeImageDataOnCopy = includeImageDataOnCopy,
                        onToggleGroup = { packageName -> viewModel.toggleGroupExpansion(packageName) },
                        onDeleteGroup = { packageName -> viewModel.deleteGroup(packageName) },
                        onDeleteEntry = { id -> viewModel.deleteEntry(id) },
                        loadEntryDetail = { id -> viewModel.loadEntryDetail(id) },
                        deleteWidthPx = deleteWidthPx,
                        deleteWidth = deleteWidth,
                    )
                }
            }
        },
    )
}
