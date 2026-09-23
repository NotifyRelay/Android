package com.xzyht.notifyrelay.ui.pages.history

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.xzyht.notifyrelay.ui.common.DoubleClickConfirmButton
import com.xzyht.notifyrelay.ui.viewmodel.GroupedNotifications
import com.xzyht.notifyrelay.ui.viewmodel.NotificationHistoryUiState
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.TextStyles

@Composable
internal fun NotificationHistoryScaffold(
    pagingItems: LazyPagingItems<GroupedNotifications>,
    uiState: NotificationHistoryUiState,
    installedPackages: Set<String>,
    getCachedAppInfo: (String?) -> Pair<String, android.graphics.Bitmap?>,
    context: Context,
    colorScheme: Colors,
    textStyles: TextStyles,
    deleteWidthPx: Float,
    deleteWidth: Dp,
    onClearHistory: () -> Unit,
    onToggleGroup: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDeleteNotification: (String) -> Unit,
) {
    // 使用 Miuix Scaffold 重构布局
    Scaffold(
        containerColor = colorScheme.background,
        floatingToolbar = {
            if (pagingItems.itemCount > 0) {
                FloatingToolbar(
                    color = colorScheme.primary,
                    cornerRadius = 20.dp,
                    showDivider = false,
                ) {
                    // 使用Row水平排列按钮
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 清除按钮 - 始终显示
                        DoubleClickConfirmButton(
                            text = "清除",
                            confirmText = "确认?",
                            onClick = {
                                // 第一次点击，显示提示信息
                            },
                            onConfirm = {
                                onClearHistory()
                            },
                            modifier = Modifier.Companion,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            confirmColors = ButtonDefaults.buttonColors(color = colorScheme.error),
                            textColor = colorScheme.onPrimary,
                            confirmTextColor = colorScheme.onError,
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
                        .padding(16.dp),
            ) {
                val isEmpty = pagingItems.itemCount == 0
                if (isEmpty) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无通知",
                        style = textStyles.body1.copy(color = colorScheme.onSurfaceSecondary),
                    )
                } else {
                    NotificationListBlock(
                        pagingItems = pagingItems,
                        getCachedAppInfo = getCachedAppInfo,
                        expandedGroups = uiState.expandedGroups,
                        installedPackages = installedPackages,
                        context = context,
                        colorScheme = colorScheme,
                        textStyles = textStyles,
                        deleteWidthPx = deleteWidthPx,
                        deleteWidth = deleteWidth,
                        onToggleGroup = onToggleGroup,
                        onDeleteGroup = onDeleteGroup,
                        onDeleteNotification = onDeleteNotification,
                    )
                }
            }
        },
    )
}
